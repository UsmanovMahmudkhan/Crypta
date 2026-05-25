package com.sovereigncomm.api;

import com.sovereigncomm.SovereignCommApplication;
import com.sovereigncomm.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SovereignCommApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendFlowIntegrationTest {
    private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", postgres::getJdbcUrl);
        registry.add("DATABASE_USERNAME", postgres::getUsername);
        registry.add("DATABASE_PASSWORD", postgres::getPassword);
        registry.add("BOOTSTRAP_TOKEN", () -> BOOTSTRAP_TOKEN);
        registry.add("WEBAUTHN_RP_ID", () -> "localhost");
        registry.add("WEBAUTHN_RP_NAME", () -> "Sovereign Comm Test");
        registry.add("WEBAUTHN_ALLOWED_ORIGINS", () -> "http://localhost");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenService tokenService;

    @Test
    void provisionsSessionsAndDeliversEncryptedMessagesThroughDatabase() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class)).isPositive();

        ResponseEntity<Map> denied = post("/api/v1/organizations",
                Map.of("name", "Denied", "jurisdiction", "US"),
                new HttpHeaders(),
                Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        HttpHeaders bootstrapHeaders = new HttpHeaders();
        bootstrapHeaders.set("X-Bootstrap-Token", BOOTSTRAP_TOKEN);

        UUID organizationId = idFrom(post("/api/v1/organizations",
                Map.of("name", "Acme Secure", "jurisdiction", "US"),
                bootstrapHeaders,
                Map.class));
        UUID senderUserId = idFrom(post("/api/v1/users",
                Map.of("organizationId", organizationId.toString(), "email", "sender@example.com", "displayName", "Sender"),
                bootstrapHeaders,
                Map.class));
        UUID recipientUserId = idFrom(post("/api/v1/users",
                Map.of("organizationId", organizationId.toString(), "email", "recipient@example.com", "displayName", "Recipient"),
                bootstrapHeaders,
                Map.class));
        UUID senderDeviceId = createDevice(senderUserId, "Sender iPhone", bootstrapHeaders);
        UUID recipientDeviceId = createDevice(recipientUserId, "Recipient Pixel", bootstrapHeaders);
        UUID outsiderUserId = idFrom(post("/api/v1/users",
                Map.of("organizationId", organizationId.toString(), "email", "outsider@example.com", "displayName", "Outsider"),
                bootstrapHeaders,
                Map.class));
        UUID outsiderDeviceId = createDevice(outsiderUserId, "Outsider Laptop", bootstrapHeaders);

        String senderToken = sessionToken(senderUserId, senderDeviceId, bootstrapHeaders);
        String recipientToken = sessionToken(recipientUserId, recipientDeviceId, bootstrapHeaders);
        String outsiderToken = sessionToken(outsiderUserId, outsiderDeviceId, bootstrapHeaders);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_sessions WHERE token_hash = ?",
                Integer.class,
                tokenService.sha256(senderToken))).isEqualTo(1);

        HttpHeaders senderHeaders = bearerHeaders(senderToken);
        UUID messageId = idFrom(post("/api/v1/messages/direct",
                directMessage(organizationId, senderUserId, senderDeviceId, recipientUserId, recipientDeviceId, Map.of("algorithm", "XChaCha20-Poly1305")),
                senderHeaders,
                Map.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM encrypted_messages WHERE id = ?",
                Integer.class,
                messageId)).isEqualTo(1);

        ResponseEntity<List> inbox = rest.exchange(url("/api/v1/messages/inbox?deviceId=" + recipientDeviceId),
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(recipientToken)),
                List.class);
        assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inbox.getBody()).hasSize(1);
        assertThat((String) ((Map<?, ?>) inbox.getBody().getFirst()).get("id")).isEqualTo(messageId.toString());

        ResponseEntity<Map> wrongSender = post("/api/v1/messages/direct",
                directMessage(organizationId, recipientUserId, recipientDeviceId, senderUserId, senderDeviceId, Map.of("algorithm", "XChaCha20-Poly1305")),
                senderHeaders,
                Map.class);
        assertThat(wrongSender.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> plaintext = post("/api/v1/messages/direct",
                directMessage(organizationId, senderUserId, senderDeviceId, recipientUserId, recipientDeviceId, Map.of("plaintext", "secret")),
                senderHeaders,
                Map.class);
        assertThat(plaintext.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> wrongReceipt = post("/api/v1/messages/receipts",
                Map.of("messageId", messageId.toString(), "deviceId", recipientDeviceId.toString(), "receiptType", "READ"),
                senderHeaders,
                Map.class);
        assertThat(wrongReceipt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> acceptedReceipt = post("/api/v1/messages/receipts",
                Map.of("messageId", messageId.toString(), "deviceId", recipientDeviceId.toString(), "receiptType", "READ"),
                bearerHeaders(recipientToken),
                Map.class);
        assertThat(acceptedReceipt.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_delivery_receipts WHERE message_id = ? AND device_id = ?",
                Integer.class,
                messageId,
                recipientDeviceId)).isEqualTo(1);

        UUID roomId = idFrom(post("/api/v1/mission-rooms",
                Map.of("organizationId", organizationId.toString(), "name", "Launch Room", "classification", "SECRET",
                        "policy", Map.of("minimumAlgorithm", "MLS-1.0")),
                senderHeaders,
                Map.class));
        ResponseEntity<Map> invite = post("/api/v1/mission-rooms/members/invite",
                Map.of("roomId", roomId.toString(), "userId", recipientUserId.toString(), "reason", "mission-participant"),
                senderHeaders,
                Map.class);
        assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID attachmentId = idFrom(post("/api/v1/attachments",
                Map.of("roomId", roomId.toString(), "objectKey", "org/%s/launch-plan.bin".formatted(organizationId),
                        "ciphertextSha256", sha256Hex("attachment-ciphertext".getBytes(StandardCharsets.UTF_8)),
                        "ciphertextBytes", 128,
                        "cryptoMetadata", Map.of("algorithm", "XChaCha20-Poly1305", "keyId", "room-key-1")),
                senderHeaders,
                Map.class));
        ResponseEntity<Map> download = rest.exchange(url("/api/v1/attachments/" + attachmentId + "/download"),
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(recipientToken)),
                Map.class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).containsEntry("downloadMode", "DATABASE_DOWNLOAD_GRANT");
        assertThat((String) download.getBody().get("downloadToken")).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM encrypted_attachment_download_grants WHERE attachment_id = ?",
                Integer.class,
                attachmentId)).isEqualTo(1);

        ResponseEntity<Map> outsiderDownload = rest.exchange(url("/api/v1/attachments/" + attachmentId + "/download"),
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(outsiderToken)),
                Map.class);
        assertThat(outsiderDownload.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        jdbc.update("""
                        INSERT INTO mdm_devices(organization_id, device_id, mdm_provider, external_device_id, compliance_state, posture, last_seen_at)
                        VALUES (?, ?, 'test-mdm', ?, 'COMPLIANT', ?::jsonb, now())
                        """,
                organizationId,
                recipientDeviceId,
                recipientDeviceId.toString(),
                "{\"hardwareBacked\":true,\"secureEnclave\":true}");
        ResponseEntity<Map> mdmSync = post("/api/v1/admin/mdm/sync/" + organizationId, Map.of(), bootstrapHeaders, Map.class);
        assertThat(mdmSync.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT trust_state FROM devices WHERE id = ?", String.class, recipientDeviceId)).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("SELECT mdm_compliant FROM devices WHERE id = ?", Boolean.class, recipientDeviceId)).isTrue();

        ResponseEntity<Map> auditExport = post("/api/v1/admin/audit/export",
                Map.of("organizationId", organizationId.toString(), "sinkType", "DATABASE_BUFFER"),
                bootstrapHeaders,
                Map.class);
        assertThat(auditExport.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM siem_export_events", Integer.class)).isPositive();

        ResponseEntity<Map> inlineExport = post("/api/v1/admin/siem/events",
                """
                        {"organizationId":"%s","sinkType":"INLINE","eventType":"CONTROL_CHECK","metadata":{"result":"pass"}}
                        """.formatted(organizationId),
                bootstrapHeaders,
                Map.class);
        assertThat(inlineExport.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UUID createDevice(UUID userId, String deviceName, HttpHeaders headers) {
        return idFrom(post("/api/v1/devices",
                Map.of(
                        "userId", userId.toString(),
                        "platform", "iOS",
                        "deviceName", deviceName,
                        "attestationFormat", "local-test",
                        "attestationObjectBase64", base64("attestation"),
                        "deviceSigningPublicKeyBase64", base64("device-key")),
                headers,
                Map.class));
    }

    private String sessionToken(UUID userId, UUID deviceId, HttpHeaders headers) {
        ResponseEntity<Map> response = post("/api/v1/bootstrap/sessions",
                Map.of("userId", userId.toString(), "deviceId", deviceId.toString()),
                headers,
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("token");
    }

    private Map<String, Object> directMessage(UUID organizationId, UUID senderUserId, UUID senderDeviceId,
                                              UUID recipientUserId, UUID recipientDeviceId,
                                              Map<String, Object> cryptoMetadata) throws Exception {
        String ciphertext = base64("ciphertext");
        return Map.of(
                "organizationId", organizationId.toString(),
                "senderUserId", senderUserId.toString(),
                "senderDeviceId", senderDeviceId.toString(),
                "recipientUserId", recipientUserId.toString(),
                "recipientDeviceId", recipientDeviceId.toString(),
                "messageKind", "DIRECT",
                "ciphertextBase64", ciphertext,
                "ciphertextSha256", sha256Hex(Base64.getDecoder().decode(ciphertext)),
                "cryptoMetadata", cryptoMetadata);
    }

    private <T> ResponseEntity<T> post(String path, Object body, HttpHeaders headers, Class<T> responseType) {
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private UUID idFrom(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return UUID.fromString((String) response.getBody().get("id"));
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
