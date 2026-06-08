package com.sovereigncomm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereigncomm.api.dto.CommonDtos.AuditExportRequest;
import com.sovereigncomm.api.dto.CommonDtos.AttachmentCreateRequest;
import com.sovereigncomm.api.dto.CommonDtos.DeliveryReceiptRequest;
import com.sovereigncomm.api.dto.CommonDtos.EncryptedMessageSubmitRequest;
import com.sovereigncomm.api.dto.CommonDtos.EmergencyLockdownRequest;
import com.sovereigncomm.api.dto.CommonDtos.OrganizationCreateRequest;
import com.sovereigncomm.api.dto.CommonDtos.PreKeyUploadRequest;
import com.sovereigncomm.api.dto.CommonDtos.PublicKeyUploadRequest;
import com.sovereigncomm.api.dto.CommonDtos.RoomCreateRequest;
import com.sovereigncomm.config.AuthenticatedActor;
import com.sovereigncomm.security.PlaintextGuard;
import com.sovereigncomm.security.SecurityVerifierClient;
import com.sovereigncomm.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcSovereignCommServicesSecurityTest {
    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final JdbcSovereignCommServices service = new JdbcSovereignCommServices(
            jdbcTemplate,
            new ObjectMapper(),
            new TokenService(""),
            new PlaintextGuard(),
            Mockito.mock(SecurityVerifierClient.class),
            "localhost",
            "Crypta Test",
            "http://localhost",
            false);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        Mockito.reset(jdbcTemplate);
    }

    @Test
    void organizationCreationRequiresBootstrapOrPlatformOperator() {
        authenticate(Set.of("ORG_ADMIN"), UUID.randomUUID());

        assertThatThrownBy(() -> service.createOrganization(new OrganizationCreateRequest("Other Org", "US", null)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Platform operator");
    }

    @Test
    void webauthnRegistrationStartRequiresAuthenticatedOnboardingActor() {
        assertThatThrownBy(() -> service.startWebAuthnRegistration(UUID.randomUUID()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Authenticated actor");
    }

    @Test
    void webauthnLoginStartIgnoresLegacyDemoCredentials() {
        UUID userId = UUID.randomUUID();
        Mockito.when(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM webauthn_credentials WHERE user_id = ? AND verification_status = 'VERIFIED'",
                        Integer.class,
                        userId))
                .thenReturn(0);

        assertThatThrownBy(() -> service.startWebAuthnLogin(userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("no registered passkey");
    }

    @Test
    void adminSecurityViewsRejectOtherOrganizationsBeforeQuerying() {
        UUID actorOrg = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        authenticate(Set.of("ORG_ADMIN"), actorOrg);

        assertThatThrownBy(() -> service.transparencyMonitor(otherOrg))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("another organization");
        assertThatThrownBy(() -> service.verifyAuditChain(otherOrg))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("another organization");
    }

    @Test
    void adminActionsAndLockdownsRejectOtherOrganizationsBeforeSideEffects() {
        UUID actorOrg = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        authenticate(Set.of("ORG_ADMIN"), actorOrg);

        String signedAction = """
                {"organizationId":"%s","actionType":"LOCKDOWN","signatureBase64":"fake"}
                """.formatted(otherOrg);

        assertThatThrownBy(() -> service.recordSignedAction(signedAction))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("another organization");
        assertThatThrownBy(() -> service.startLockdown(new EmergencyLockdownRequest(
                otherOrg,
                null,
                "incident",
                "ORGANIZATION")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("another organization");
    }

    @Test
    void bootstrapCannotUseNonOnboardingServicePaths() {
        authenticateBootstrap();
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.recordSignedAction("""
                {"organizationId":"%s","actionType":"LOCKDOWN","signatureBase64":"fake"}
                """.formatted(id)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.transparencyMonitor(id))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.revokeDevice(id, "test"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.uploadIdentityKey(new PublicKeyUploadRequest(id, "TEST", "ZmFrZQ==", "ZmFrZQ==")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.recordReceipt(new DeliveryReceiptRequest(id, id, "READ")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.uploadSignedPreKey(new PreKeyUploadRequest(id, "key-1", "TEST", "ZmFrZQ==", "ZmFrZQ==")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.createMissionRoom(new RoomCreateRequest(id, "Denied", "SECRET", Map.of())))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Bearer session");
        assertThatThrownBy(() -> service.submit(new EncryptedMessageSubmitRequest(
                id,
                null,
                id,
                id,
                id,
                id,
                "DIRECT",
                "ZmFrZQ==",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                Map.of("algorithm", "TEST"))))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Bearer session");
        assertThatThrownBy(() -> service.createEncryptedAttachment(new AttachmentCreateRequest(
                id,
                "org/file.bin",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                1,
                Map.of("algorithm", "TEST"))))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.exportAudit(new AuditExportRequest(id, "DATABASE_BUFFER", Instant.EPOCH, Instant.now())))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.exportEvent("""
                {"organizationId":"%s","sinkType":"INLINE","metadata":{"result":"ok"}}
                """.formatted(id)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.syncDevicePosture(id))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.verifyAuditChain(id))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
        assertThatThrownBy(() -> service.startLockdown(new EmergencyLockdownRequest(id, null, "incident", "ORGANIZATION")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer session");
    }

    @Test
    void roomLockdownRejectsMismatchedOrganizationAndRoom() {
        UUID actorOrg = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        authenticate(Set.of("ORG_ADMIN"), actorOrg);
        Mockito.when(jdbcTemplate.query(
                        Mockito.startsWith("SELECT organization_id FROM rooms"),
                        Mockito.any(ResultSetExtractor.class),
                        Mockito.eq(roomId)))
                .thenReturn(otherOrg);

        assertThatThrownBy(() -> service.startLockdown(new EmergencyLockdownRequest(actorOrg, roomId, "incident", "ROOM")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("room must belong");
    }

    private void authenticate(Set<String> roles, UUID organizationId) {
        AuthenticatedActor actor = new AuthenticatedActor(UUID.randomUUID(), organizationId, UUID.randomUUID(), roles, false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, "N/A"));
    }

    private void authenticateBootstrap() {
        AuthenticatedActor actor = new AuthenticatedActor(null, null, null, Set.of("BOOTSTRAP"), true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, "N/A"));
    }
}
