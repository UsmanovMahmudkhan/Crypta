package com.sovereigncomm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereigncomm.api.dto.CommonDtos.*;
import com.sovereigncomm.config.AuthenticatedActor;
import com.sovereigncomm.security.PlaintextGuard;
import com.sovereigncomm.security.SecurityVerifierClient;
import com.sovereigncomm.security.TokenService;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
class JdbcSovereignCommServices implements AuthService, OrganizationService, UserService, DeviceTrustService, KeyService,
        PreKeyService, KeyTransparencyService, DirectMessageService, GroupMessageService, MLSGroupService,
        RoomPolicyService, DeliveryService, NotificationService, AttachmentService, AuditService,
        AdminGovernanceService, MDMIntegrationService, SIEMExportService, EmergencyLockdownService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> ALLOWED_ROLES = Set.of("MEMBER", "ADMIN", "ORG_ADMIN", "PLATFORM_OPERATOR");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final PlaintextGuard plaintextGuard;
    private final SecurityVerifierClient securityVerifierClient;
    private final String webauthnRpId;
    private final String webauthnRpName;
    private final String allowedOrigins;
    private final boolean requireVerifiedDevicesForSessions;

    JdbcSovereignCommServices(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            TokenService tokenService,
            PlaintextGuard plaintextGuard,
            SecurityVerifierClient securityVerifierClient,
            @Value("${security.webauthn.rp-id:localhost}") String webauthnRpId,
            @Value("${security.webauthn.rp-name:Sovereign Comm}") String webauthnRpName,
            @Value("${security.webauthn.allowed-origins:http://localhost:8080}") String allowedOrigins,
            @Value("${app.security.require-verified-devices-for-sessions:false}") boolean requireVerifiedDevicesForSessions) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.plaintextGuard = plaintextGuard;
        this.securityVerifierClient = securityVerifierClient;
        this.webauthnRpId = webauthnRpId;
        this.webauthnRpName = webauthnRpName;
        this.allowedOrigins = allowedOrigins;
        this.requireVerifiedDevicesForSessions = requireVerifiedDevicesForSessions;
    }

    @Override
    @Transactional
    public WebAuthnStartResponse startWebAuthnRegistration(UUID userId) {
        requireUserExists(userId);
        requireWebAuthnRegistrationActor(userId);
        PublicKeyCredentialCreationOptions options = relyingParty().startRegistration(StartRegistrationOptions.builder()
                .user(userIdentity(userId))
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .timeout(300000)
                .build());
        return storeWebAuthnChallenge(userId, "REGISTRATION", options.getChallenge().getBase64Url(), toYubicoMap(options));
    }

    @Override
    @Transactional
    public SessionResponse issueBootstrapSession(BootstrapSessionRequest request) {
        AuthenticatedActor actor = requireActor();
        if (!actor.bootstrap()) {
            throw new SecurityException("Bootstrap token is required to issue bootstrap sessions");
        }
        if (!request.userId().equals(userForDevice(request.deviceId()))) {
            throw new SecurityException("Device does not belong to requested user");
        }
        return issueSession(request.userId(), request.deviceId());
    }

    @Override
    @Transactional
    public SessionResponse finishWebAuthnRegistration(WebAuthnFinishRequest request) {
        requireUserExists(request.userId());
        requireWebAuthnRegistrationActor(request.userId());
        WebAuthnChallenge challenge = consumeChallenge(request.userId(), "REGISTRATION");
        RegistrationResult result;
        try {
            result = relyingParty().finishRegistration(FinishRegistrationOptions.builder()
                    .request(PublicKeyCredentialCreationOptions.fromJson(challenge.requestOptionsJson()))
                    .response(PublicKeyCredential.parseRegistrationResponseJson(request.credentialJson()))
                    .build());
        } catch (Exception e) {
            throw new SecurityException("WebAuthn registration verification failed", e);
        }
        int inserted = jdbc.update("""
                        INSERT INTO webauthn_credentials(
                            user_id, credential_id, public_key_cose, signature_count, transports,
                            attestation_type, backup_eligible, backup_state, verification_status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'VERIFIED')
                        ON CONFLICT (credential_id) DO NOTHING
                        """,
                request.userId(),
                result.getKeyId().getId().getBytes(),
                result.getPublicKeyCose().getBytes(),
                result.getSignatureCount(),
                result.getKeyId().getTransports()
                        .map(transports -> transports.stream().map(AuthenticatorTransport::getId).toArray(String[]::new))
                        .orElseGet(() -> new String[0]),
                result.getAttestationType().name(),
                result.isBackupEligible(),
                result.isBackedUp());
        if (inserted != 1) {
            throw new SecurityException("Passkey credential is already registered");
        }
        UUID deviceId = request.deviceId() == null ? latestDeviceForUser(request.userId()) : request.deviceId();
        appendSecurityEventForUser(request.userId(), "WEBAUTHN_CREDENTIAL_REGISTERED", Map.of("credentialIdHashBase64",
                Base64.getEncoder().encodeToString(tokenService.sha256(result.getKeyId().getId().getBytes()))));
        return issueSession(request.userId(), deviceId);
    }

    @Override
    @Transactional
    public WebAuthnStartResponse startWebAuthnLogin(UUID userId) {
        requireUserExists(userId);
        Integer credentials = jdbc.queryForObject("SELECT count(*) FROM webauthn_credentials WHERE user_id = ? AND verification_status = 'VERIFIED'", Integer.class, userId);
        if (credentials == null || credentials == 0) {
            throw new SecurityException("User has no registered passkey");
        }
        AssertionRequest request = relyingParty().startAssertion(StartAssertionOptions.builder()
                .username(userId.toString())
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(300000)
                .build());
        return storeWebAuthnChallenge(userId, "LOGIN",
                request.getPublicKeyCredentialRequestOptions().getChallenge().getBase64Url(),
                toYubicoMap(request));
    }

    @Override
    @Transactional
    public SessionResponse finishWebAuthnLogin(WebAuthnFinishRequest request) {
        requireUserExists(request.userId());
        WebAuthnChallenge challenge = consumeChallenge(request.userId(), "LOGIN");
        AssertionResult result;
        try {
            result = relyingParty().finishAssertion(FinishAssertionOptions.builder()
                    .request(AssertionRequest.fromJson(challenge.requestOptionsJson()))
                    .response(PublicKeyCredential.parseAssertionResponseJson(request.credentialJson()))
                    .build());
        } catch (Exception e) {
            throw new SecurityException("WebAuthn login verification failed", e);
        }
        if (!result.isSuccess() || !request.userId().toString().equals(result.getUsername())) {
            throw new SecurityException("Passkey assertion is not valid for the requested user");
        }
        Integer updated = jdbc.update("""
                        UPDATE webauthn_credentials
                        SET signature_count = ?,
                            last_used_at = now()
                        WHERE user_id = ? AND credential_id = ?
                          AND verification_status = 'VERIFIED'
                        """,
                result.getSignatureCount(), request.userId(), result.getCredentialId().getBytes());
        if (updated == null || updated != 1) {
            throw new SecurityException("Passkey credential is not registered for the requested user");
        }
        UUID deviceId = request.deviceId() == null ? latestDeviceForUser(request.userId()) : request.deviceId();
        return issueSession(request.userId(), deviceId);
    }

    @Override
    @Transactional
    public IdResponse createOrganization(OrganizationCreateRequest request) {
        IdResponse response = returning("""
                        INSERT INTO organizations(name, jurisdiction, external_tenant_id)
                        VALUES (?, ?, ?)
                        RETURNING id, created_at
                        """,
                request.name(), request.jurisdiction(), request.externalTenantId());
        appendSecurityEvent(response.id(), null, null, "ORGANIZATION_CREATED", Map.of("jurisdiction", request.jurisdiction()));
        return response;
    }

    @Override
    @Transactional
    public IdResponse registerUser(UserRegisterRequest request) {
        AuthenticatedActor actor = actorOrNull();
        if (actor != null && !actor.bootstrap() && !request.organizationId().equals(actor.organizationId())) {
            throw new SecurityException("Cannot create users outside the actor organization");
        }
        IdResponse response = returning("""
                        INSERT INTO users(organization_id, email, display_name)
                        VALUES (?, ?, ?)
                        RETURNING id, created_at
                        """,
                request.organizationId(), request.email(), request.displayName());
        List<String> roles = request.roles() == null || request.roles().isEmpty() ? List.of("MEMBER") : request.roles();
        for (String role : roles) {
            jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, ?) ON CONFLICT DO NOTHING", response.id(), normalizeRole(role));
        }
        appendSecurityEvent(request.organizationId(), actor == null ? null : actor.userId(), actor == null ? null : actor.deviceId(),
                "USER_REGISTERED", Map.of("userId", response.id().toString(), "roles", roles));
        return response;
    }

    @Override
    @Transactional
    public IdResponse registerDevice(DeviceRegisterRequest request) {
        UUID orgId = organizationForUser(request.userId());
        AuthenticatedActor actor = requireActor();
        requireOrgAccess(actor, orgId);
        if (!actor.bootstrap() && !actor.userId().equals(request.userId()) && !hasOrgAdminRole(actor)) {
            throw new SecurityException("Cannot register a device for another user");
        }
        byte[] signingKey = decodeBase64(request.deviceSigningPublicKeyBase64(), "deviceSigningPublicKeyBase64");
        byte[] attestation = decodeBase64(request.attestationObjectBase64(), "attestationObjectBase64");
        IdResponse response = returning("""
                        INSERT INTO devices(user_id, organization_id, platform, device_name, device_signing_public_key)
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id, created_at
                        """,
                request.userId(), orgId, request.platform(), request.deviceName(), signingKey);
        SecurityVerifierClient.DeviceAttestationVerificationResponse attestationVerification =
                securityVerifierClient.verifyDeviceAttestation(new SecurityVerifierClient.DeviceAttestationVerificationRequest(
                        orgId, request.userId(), response.id(), request.attestationFormat(), request.attestationObjectBase64()));
        if (!attestationVerification.valid()) {
            throw new SecurityException("Device attestation was rejected by verifier");
        }
        jdbc.update("""
                        INSERT INTO device_attestations(device_id, attestation_format, attestation_statement, verification_status, verified_claims)
                        VALUES (?, ?, ?, ?, ?::jsonb)
                        """,
                response.id(), request.attestationFormat(), attestation,
                attestationVerification.verificationStatus(), toJson(attestationVerification.verifiedClaims()));
        jdbc.update("""
                        UPDATE devices
                        SET hardware_backed = ?, strongbox_or_secure_enclave = ?,
                            trust_state = CASE WHEN ? THEN 'VERIFIED' ELSE trust_state END
                        WHERE id = ?
                        """,
                attestationVerification.hardwareBacked(),
                attestationVerification.strongBoxOrSecureEnclave(),
                attestationVerification.hardwareBacked() && attestationVerification.strongBoxOrSecureEnclave(),
                response.id());
        appendSecurityEvent(orgId, request.userId(), response.id(), "DEVICE_REGISTERED", Map.of("platform", request.platform()));
        return response;
    }

    @Override
    @Transactional
    public void revokeDevice(UUID deviceId, String reason) {
        UUID orgId = organizationForDevice(deviceId);
        requireDeviceActorOrOrgAdmin(deviceId, orgId);
        jdbc.update("UPDATE devices SET trust_state = 'REVOKED', revoked_at = now(), revoke_reason = ? WHERE id = ?", reason, deviceId);
        jdbc.update("UPDATE api_sessions SET revoked_at = now() WHERE device_id = ?", deviceId);
        appendSecurityEvent(orgId, null, deviceId, "DEVICE_REVOKED", Map.of("reason", reason));
    }

    @Override
    @Transactional
    public void uploadIdentityKey(PublicKeyUploadRequest request) {
        UUID orgId = organizationForDevice(request.deviceId());
        requireDeviceActor(request.deviceId());
        byte[] publicKey = decodeBase64(request.publicKeyBase64(), "publicKeyBase64");
        byte[] signature = decodeBase64(request.signatureBase64(), "signatureBase64");
        Integer nextVersion = jdbc.queryForObject(
                "SELECT COALESCE(MAX(key_version), 0) + 1 FROM identity_public_keys WHERE device_id = ?",
                Integer.class,
                request.deviceId());
        jdbc.update("UPDATE identity_public_keys SET active = false WHERE device_id = ?", request.deviceId());
        jdbc.update("""
                        INSERT INTO identity_public_keys(device_id, algorithm, public_key, signature, key_version)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                request.deviceId(), request.algorithm(), publicKey, signature, nextVersion);
        appendKeyEvent(request.deviceId(), toJson(Map.of(
                "deviceId", request.deviceId().toString(),
                "algorithm", request.algorithm(),
                "keyVersion", nextVersion)), request.signatureBase64());
        appendSecurityEvent(orgId, null, request.deviceId(), "IDENTITY_KEY_UPLOADED", Map.of("algorithm", request.algorithm(), "keyVersion", nextVersion));
    }

    @Override
    @Transactional
    public KeyBundleResponse fetchKeyBundle(UUID userId) {
        requireUserExists(userId);
        List<Map<String, Object>> identityKeys = jdbc.query("""
                        SELECT d.id AS device_id, k.algorithm, k.public_key, k.signature, k.key_version, k.created_at
                        FROM devices d
                        JOIN identity_public_keys k ON k.device_id = d.id
                        WHERE d.user_id = ? AND k.active = true AND d.revoked_at IS NULL
                        ORDER BY k.created_at DESC
                        """,
                (rs, rowNum) -> Map.of(
                        "deviceId", rs.getObject("device_id", UUID.class).toString(),
                        "algorithm", rs.getString("algorithm"),
                        "publicKeyBase64", Base64.getEncoder().encodeToString(rs.getBytes("public_key")),
                        "signatureBase64", Base64.getEncoder().encodeToString(rs.getBytes("signature")),
                        "keyVersion", rs.getInt("key_version"),
                        "createdAt", rs.getTimestamp("created_at").toInstant().toString()),
                userId);
        List<Map<String, Object>> prekeys = new ArrayList<>();
        prekeys.addAll(prekeys(userId, "signed_prekeys", "SIGNED"));
        prekeys.addAll(prekeys(userId, "one_time_prekeys", "ONE_TIME"));
        prekeys.addAll(prekeys(userId, "pq_prekeys", "PQ"));
        UUID firstDevice = jdbc.query("SELECT id FROM devices WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                userId);
        KeyTransparencyProofResponse proof = proofResponse(userId);
        return new KeyBundleResponse(userId, firstDevice, identityKeys, prekeys, Map.of(
                "latestLogIndex", proof.latestLogIndex(),
                "signedTreeHeadBase64", proof.latestSignedTreeHeadBase64(),
                "consistencyProofBase64", proof.consistencyProofBase64(),
                "checkpointId", proof.checkpointId(),
                "proofVersion", proof.proofVersion()));
    }

    @Override
    @Transactional
    public void uploadSignedPreKey(PreKeyUploadRequest request) {
        uploadPrekey("signed_prekeys", request, true, true);
    }

    @Override
    @Transactional
    public void uploadOneTimePreKey(PreKeyUploadRequest request) {
        uploadPrekey("one_time_prekeys", request, false, false);
    }

    @Override
    @Transactional
    public void uploadPqPreKey(PreKeyUploadRequest request) {
        uploadPrekey("pq_prekeys", request, true, true);
    }

    @Override
    @Transactional
    public void appendKeyEvent(UUID deviceId, String canonicalEntryJson, String entrySignatureBase64) {
        UUID orgId = organizationForDevice(deviceId);
        UUID userId = userForDevice(deviceId);
        byte[] canonicalHash = tokenService.sha256(canonicalEntryJson);
        byte[] signatureHash = tokenService.sha256(entrySignatureBase64);
        byte[] previousTreeHead = latestHash("key_transparency_entries", "signed_tree_head", "organization_id", orgId);
        long logIndex = Optional.ofNullable(jdbc.queryForObject("SELECT COALESCE(MAX(log_index), -1) + 1 FROM key_transparency_entries", Long.class)).orElse(0L);
        SecurityVerifierClient.KeyTransparencyAppendResponse verifierResponse = securityVerifierClient.appendKeyTransparencyEntry(
                new SecurityVerifierClient.KeyTransparencyAppendRequest(
                        orgId,
                        userId,
                        deviceId,
                        Base64.getEncoder().encodeToString(canonicalHash),
                        previousTreeHead == null ? "" : Base64.getEncoder().encodeToString(previousTreeHead),
                        logIndex));
        byte[] leafHash = decodeBase64(verifierResponse.merkleLeafHashBase64(), "merkleLeafHashBase64");
        byte[] signedTreeHead = decodeBase64(verifierResponse.signedTreeHeadBase64(), "signedTreeHeadBase64");
        jdbc.update("""
                        INSERT INTO key_transparency_entries(
                            organization_id, subject_user_id, subject_device_id, entry_type, canonical_entry_hash,
                            previous_entry_hash, merkle_leaf_hash, log_index, signed_tree_head, inclusion_proof,
                            consistency_proof, checkpoint_id, proof_version)
                        VALUES (?, ?, ?, 'IDENTITY_KEY', ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                        """,
                orgId, userId, deviceId, canonicalHash, previousTreeHead, leafHash, logIndex, signedTreeHead,
                toJson(verifierResponse.inclusionProof()),
                verifierResponse.consistencyProofBase64() == null || verifierResponse.consistencyProofBase64().isBlank()
                        ? null
                        : decodeBase64(verifierResponse.consistencyProofBase64(), "consistencyProofBase64"),
                verifierResponse.checkpointId(),
                verifierResponse.proofVersion());
    }

    @Override
    public Map<String, Object> proof(UUID userId) {
        KeyTransparencyProofResponse response = proofResponse(userId);
        return Map.of(
                "userId", response.userId(),
                "latestLogIndex", response.latestLogIndex(),
                "latestSignedTreeHeadBase64", response.latestSignedTreeHeadBase64(),
                "consistencyProofBase64", response.consistencyProofBase64(),
                "checkpointId", response.checkpointId(),
                "proofVersion", response.proofVersion(),
                "entries", response.entries());
    }

    @Override
    public KeyTransparencyProofResponse proofResponse(UUID userId) {
        List<Map<String, Object>> entries = jdbc.query("""
                        SELECT log_index, canonical_entry_hash, previous_entry_hash, merkle_leaf_hash, signed_tree_head,
                               inclusion_proof::text, consistency_proof, checkpoint_id, proof_version, created_at
                        FROM key_transparency_entries
                        WHERE subject_user_id = ?
                        ORDER BY log_index
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("logIndex", rs.getLong("log_index"));
                    entry.put("canonicalEntryHashBase64", Base64.getEncoder().encodeToString(rs.getBytes("canonical_entry_hash")));
                    byte[] previous = rs.getBytes("previous_entry_hash");
                    entry.put("previousEntryHashBase64", previous == null ? null : Base64.getEncoder().encodeToString(previous));
                    entry.put("merkleLeafHashBase64", Base64.getEncoder().encodeToString(rs.getBytes("merkle_leaf_hash")));
                    entry.put("signedTreeHeadBase64", Base64.getEncoder().encodeToString(rs.getBytes("signed_tree_head")));
                    entry.put("inclusionProof", fromJson(rs.getString("inclusion_proof")));
                    byte[] consistency = rs.getBytes("consistency_proof");
                    entry.put("consistencyProofBase64", consistency == null ? "" : Base64.getEncoder().encodeToString(consistency));
                    entry.put("checkpointId", rs.getString("checkpoint_id"));
                    entry.put("proofVersion", rs.getString("proof_version"));
                    entry.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    return entry;
                },
                userId);
        long latestIndex = entries.isEmpty() ? -1L : ((Number) entries.get(entries.size() - 1).get("logIndex")).longValue();
        String latestSth = entries.isEmpty() ? "" : String.valueOf(entries.get(entries.size() - 1).get("signedTreeHeadBase64"));
        String consistencyProof = entries.isEmpty() ? "" : String.valueOf(entries.get(entries.size() - 1).get("consistencyProofBase64"));
        String checkpointId = entries.isEmpty() ? "" : String.valueOf(entries.get(entries.size() - 1).get("checkpointId"));
        String proofVersion = entries.isEmpty() ? "verifier-proof-v1" : String.valueOf(entries.get(entries.size() - 1).get("proofVersion"));
        return new KeyTransparencyProofResponse(userId, latestIndex, latestSth, consistencyProof, checkpointId, proofVersion, entries);
    }

    @Override
    public void acceptCiphertextEnvelope(String encryptedDirectMessageEnvelope) {
        assertNoPlaintext(Map.of("encryptedDirectMessageEnvelope", encryptedDirectMessageEnvelope));
    }

    @Override
    @Transactional
    public IdResponse submit(EncryptedMessageSubmitRequest request) {
        enforceMessageSender(request);
        assertNoPlaintext(request.cryptoMetadata());
        if (!Set.of("DIRECT", "MLS_GROUP").contains(request.messageKind())) {
            throw new IllegalArgumentException("messageKind must be DIRECT or MLS_GROUP");
        }
        if ("DIRECT".equals(request.messageKind()) && (request.recipientUserId() == null || request.recipientDeviceId() == null)) {
            throw new IllegalArgumentException("Direct messages require recipientUserId and recipientDeviceId");
        }
        if ("DIRECT".equals(request.messageKind())) {
            enforceNoPqDowngrade(request.recipientDeviceId(), request.cryptoMetadata());
        }
        if ("MLS_GROUP".equals(request.messageKind()) && request.roomId() == null) {
            throw new IllegalArgumentException("MLS group messages require roomId");
        }
        enforceNoActiveLockdown(request.organizationId(), request.roomId());
        IdResponse response = returning("""
                        INSERT INTO encrypted_messages(
                            organization_id, room_id, sender_user_id, sender_device_id, recipient_user_id, recipient_device_id,
                            message_kind, ciphertext, ciphertext_sha256, crypto_metadata)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        RETURNING id, server_received_at AS created_at
                        """,
                request.organizationId(),
                request.roomId(),
                request.senderUserId(),
                request.senderDeviceId(),
                request.recipientUserId(),
                request.recipientDeviceId(),
                request.messageKind(),
                decodeBase64(request.ciphertextBase64(), "ciphertextBase64"),
                decodeFlexibleHash(request.ciphertextSha256(), "ciphertextSha256"),
                toJson(nullToEmpty(request.cryptoMetadata())));
        appendSecurityEvent(request.organizationId(), request.senderUserId(), request.senderDeviceId(),
                "CIPHERTEXT_ACCEPTED", Map.of("messageId", response.id().toString(), "messageKind", request.messageKind()));
        return response;
    }

    private void enforceMessageSender(EncryptedMessageSubmitRequest request) {
        AuthenticatedActor actor = requireActor();
        if (actor.bootstrap()) {
            throw new SecurityException("Bearer session is required to submit messages");
        }
        requireOrgAccess(actor, request.organizationId());
        if (!request.senderUserId().equals(actor.userId()) || !request.senderDeviceId().equals(actor.deviceId())) {
            throw new SecurityException("Sender must match authenticated session");
        }
    }

    private void enforceNoPqDowngrade(UUID recipientDeviceId, Map<String, Object> cryptoMetadata) {
        Integer pqPrekeys = jdbc.queryForObject("""
                        SELECT count(*)
                        FROM pq_prekeys
                        WHERE device_id = ?
                          AND claimed_at IS NULL
                          AND (expires_at IS NULL OR expires_at > now())
                        """,
                Integer.class,
                recipientDeviceId);
        if (pqPrekeys == null || pqPrekeys == 0) {
            return;
        }
        String algorithm = String.valueOf(nullToEmpty(cryptoMetadata).getOrDefault("algorithm", ""));
        if (!algorithm.toUpperCase(Locale.ROOT).contains("PQXDH")) {
            throw new SecurityException("Recipient device advertises PQ support; downgrade to non-PQ direct messaging is blocked");
        }
    }

    @Override
    public List<EncryptedMessageResponse> inbox(MessageInboxRequest request) {
        enforceInboxDevice(request.deviceId());
        int limit = request.limit() == null ? 50 : Math.min(request.limit(), 100);
        Instant after = request.after() == null ? Instant.EPOCH : request.after();
        return jdbc.query("""
                        SELECT id, organization_id, room_id, sender_user_id, sender_device_id, recipient_user_id, recipient_device_id,
                               message_kind, ciphertext, ciphertext_sha256, crypto_metadata::text, server_received_at
                        FROM encrypted_messages
                        WHERE deleted_at IS NULL
                          AND server_received_at > ?
                          AND (
                              recipient_device_id = ?
                              OR room_id IN (
                                  SELECT rm.room_id
                                  FROM room_members rm
                                  JOIN devices d ON d.user_id = rm.user_id
                                  WHERE d.id = ? AND rm.membership_state = 'ACTIVE'
                              )
                          )
                        ORDER BY server_received_at
                        LIMIT ?
                        """,
                (rs, rowNum) -> messageResponse(rs),
                Timestamp.from(after), request.deviceId(), request.deviceId(), limit);
    }

    private void enforceInboxDevice(UUID deviceId) {
        AuthenticatedActor actor = requireActor();
        if (actor.bootstrap()) {
            throw new SecurityException("Bearer session is required to read inbox");
        }
        if (!deviceId.equals(actor.deviceId())) {
            throw new SecurityException("Inbox device must match authenticated session");
        }
    }

    @Override
    @Transactional
    public void recordReceipt(DeliveryReceiptRequest request) {
        requireDeviceActor(request.deviceId());
        requireMessageVisibleToDevice(request.messageId(), request.deviceId());
        jdbc.update("""
                        INSERT INTO message_delivery_receipts(message_id, device_id, receipt_type)
                        VALUES (?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                request.messageId(), request.deviceId(), request.receiptType());
    }

    @Override
    @Transactional
    public IdResponse createMissionRoom(RoomCreateRequest request) {
        AuthenticatedActor actor = requireActor();
        if (actor.bootstrap()) {
            throw new SecurityException("Bearer session is required to create mission rooms");
        }
        requireOrgAccess(actor, request.organizationId());
        enforceNoActiveLockdown(request.organizationId(), null);
        IdResponse response = returning("""
                        INSERT INTO rooms(organization_id, name, classification, created_by)
                        VALUES (?, ?, ?, ?)
                        RETURNING id, created_at
                        """,
                request.organizationId(), request.name(), request.classification(), actor.userId());
        jdbc.update("""
                        INSERT INTO room_members(room_id, user_id, role)
                        VALUES (?, ?, 'OWNER')
                        ON CONFLICT DO NOTHING
                        """,
                response.id(), actor.userId());
        jdbc.update("""
                        INSERT INTO room_policies(room_id, min_device_trust, policy_json, version)
                        VALUES (?, 'PENDING_VERIFICATION', ?::jsonb, 1)
                        """,
                response.id(), toJson(nullToEmpty(request.policy())));
        appendSecurityEvent(request.organizationId(), actor.userId(), actor.deviceId(), "MISSION_ROOM_CREATED", Map.of("roomId", response.id().toString()));
        return response;
    }

    @Override
    @Transactional
    public void inviteMember(MemberChangeRequest request) {
        UUID orgId = organizationForRoom(request.roomId());
        requireRoomAdmin(request.roomId(), orgId);
        if (!orgId.equals(organizationForUser(request.userId()))) {
            throw new SecurityException("Cannot invite users from another organization");
        }
        enforceNoActiveLockdown(orgId, request.roomId());
        jdbc.update("""
                        INSERT INTO room_members(room_id, user_id, role, membership_state)
                        VALUES (?, ?, 'MEMBER', 'ACTIVE')
                        ON CONFLICT (room_id, user_id)
                        DO UPDATE SET membership_state = 'ACTIVE', removed_at = NULL
                        """,
                request.roomId(), request.userId());
        appendSecurityEvent(orgId, request.userId(), null, "ROOM_MEMBER_INVITED", Map.of("roomId", request.roomId().toString(), "reason", request.reason()));
    }

    @Override
    @Transactional
    public void removeMember(MemberChangeRequest request) {
        UUID orgId = organizationForRoom(request.roomId());
        requireRoomAdmin(request.roomId(), orgId);
        jdbc.update("""
                        UPDATE room_members
                        SET membership_state = 'REMOVED', removed_at = now()
                        WHERE room_id = ? AND user_id = ?
                        """,
                request.roomId(), request.userId());
        appendSecurityEvent(orgId, request.userId(), null, "ROOM_MEMBER_REMOVED", Map.of("roomId", request.roomId().toString(), "reason", request.reason()));
    }

    @Override
    @Transactional
    public void updatePolicy(RoomPolicyUpdateRequest request) {
        UUID orgId = organizationForRoom(request.roomId());
        requireRoomAdmin(request.roomId(), orgId);
        assertNoPlaintext(request.policy());
        Integer version = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) + 1 FROM room_policies WHERE room_id = ?", Integer.class, request.roomId());
        jdbc.update("""
                        INSERT INTO room_policies(room_id, min_device_trust, policy_json, version)
                        VALUES (?, 'VERIFIED', ?::jsonb, ?)
                        """,
                request.roomId(), toJson(nullToEmpty(request.policy())), version);
        appendSecurityEvent(orgId, null, null, "ROOM_POLICY_UPDATED", Map.of("roomId", request.roomId().toString(), "reason", request.reason(), "version", version));
    }

    @Override
    public void enqueueForRecipient(UUID recipientDeviceId, String encryptedEnvelope) {
        assertNoPlaintext(Map.of("encryptedEnvelope", encryptedEnvelope));
        appendSecurityEvent(organizationForDevice(recipientDeviceId), null, recipientDeviceId, "DELIVERY_ENQUEUED", Map.of("mode", "database_inbox"));
    }

    @Override
    public void sendGenericNotification(UUID deviceId, String urgency) {
        appendSecurityEvent(organizationForDevice(deviceId), null, deviceId, "GENERIC_NOTIFICATION_REQUESTED", Map.of("urgency", urgency));
    }

    @Override
    @Transactional
    public IdResponse createEncryptedAttachment(AttachmentCreateRequest request) {
        AuthenticatedActor actor = requireActor();
        UUID orgId = organizationForRoom(request.roomId());
        requireOrgAccess(actor, orgId);
        assertNoPlaintext(request.cryptoMetadata());
        if (request.ciphertextBytes() <= 0) {
            throw new IllegalArgumentException("ciphertextBytes must be positive");
        }
        if (request.objectKey().contains("..") || request.objectKey().startsWith("/")) {
            throw new IllegalArgumentException("objectKey must be a scoped object-storage key");
        }
        enforceNoActiveLockdown(orgId, request.roomId());
        UUID uploaderDevice = actor.deviceId() == null ? latestDeviceForUser(actor.userId()) : actor.deviceId();
        if (uploaderDevice == null) {
            throw new SecurityException("Attachment upload requires an enrolled device");
        }
        IdResponse response = returning("""
                        INSERT INTO encrypted_attachments(
                            organization_id, room_id, uploader_user_id, uploader_device_id, object_key,
                            ciphertext_sha256, ciphertext_bytes, crypto_metadata)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        RETURNING id, created_at
                        """,
                orgId, request.roomId(), actor.userId(), uploaderDevice, request.objectKey(),
                decodeFlexibleHash(request.ciphertextSha256(), "ciphertextSha256"), request.ciphertextBytes(),
                toJson(nullToEmpty(request.cryptoMetadata())));
        appendSecurityEvent(orgId, actor.userId(), uploaderDevice, "ENCRYPTED_ATTACHMENT_CREATED", Map.of("attachmentId", response.id().toString()));
        return response;
    }

    @Override
    public Map<String, Object> createEncryptedDownload(UUID attachmentId) {
        return jdbc.query("""
                        SELECT organization_id, room_id, object_key, ciphertext_sha256, ciphertext_bytes, crypto_metadata::text
                        FROM encrypted_attachments
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("Attachment not found");
                    }
                    UUID organizationId = rs.getObject("organization_id", UUID.class);
                    UUID roomId = rs.getObject("room_id", UUID.class);
                    AuthenticatedActor actor = requireActor();
                    requireOrgAccess(actor, organizationId);
                    requireRoomMember(actor, roomId);
                    Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);
                    String objectKey = rs.getString("object_key");
                    byte[] ciphertextHash = rs.getBytes("ciphertext_sha256");
                    String downloadToken = tokenService.newToken();
                    jdbc.update("""
                                    INSERT INTO encrypted_attachment_download_grants(
                                        attachment_id, requester_user_id, requester_device_id, token_hash, expires_at)
                                    VALUES (?, ?, ?, ?, ?)
                                    """,
                            attachmentId, actor.userId(), actor.deviceId(), tokenService.sha256(downloadToken), Timestamp.from(expiresAt));
                    return Map.of(
                            "attachmentId", attachmentId,
                            "objectKey", objectKey,
                            "ciphertextSha256", Base64.getEncoder().encodeToString(ciphertextHash),
                            "ciphertextBytes", rs.getLong("ciphertext_bytes"),
                            "cryptoMetadata", fromJson(rs.getString("crypto_metadata")),
                            "downloadMode", "OBJECT_STORAGE_SIGNED_GRANT",
                            "downloadToken", downloadToken,
                            "downloadUrl", "/object-storage/download/" + downloadToken,
                            "grantSignatureBase64", Base64.getEncoder().encodeToString(tokenService.sha256(downloadToken + ":" + objectKey)),
                            "expiresAt", expiresAt.toString());
                },
                attachmentId);
    }

    @Override
    @Transactional
    public void appendSecurityEvent(String eventType, Map<String, Object> metadata) {
        AuthenticatedActor actor = actorOrNull();
        UUID orgId = actor == null ? null : actor.organizationId();
        if (orgId == null && metadata != null && metadata.get("organizationId") != null) {
            orgId = UUID.fromString(String.valueOf(metadata.get("organizationId")));
        }
        if (orgId == null) {
            return;
        }
        appendSecurityEvent(orgId, actor == null ? null : actor.userId(), actor == null ? null : actor.deviceId(), eventType, metadata);
    }

    @Override
    @Transactional
    public void exportAudit(AuditExportRequest request) {
        requireOrgAccess(requireActor(), request.organizationId());
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new IllegalArgumentException("Audit export from must be before to");
        }
        UUID exportId = upsertSiemExport(request.organizationId(), request.sinkType());
        List<Map<String, Object>> events = jdbc.query("""
                        SELECT id, event_type, metadata::text, created_at
                        FROM audit_events
                        WHERE organization_id = ?
                          AND (?::timestamptz IS NULL OR created_at >= ?)
                          AND (?::timestamptz IS NULL OR created_at <= ?)
                          AND NOT EXISTS (
                              SELECT 1
                              FROM siem_export_events exported
                              WHERE exported.siem_export_id = ?
                                AND exported.audit_event_id = audit_events.id
                          )
                        ORDER BY created_at, id
                        LIMIT 1000
                        """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getObject("id", UUID.class),
                        "eventType", rs.getString("event_type"),
                        "metadata", fromJson(rs.getString("metadata")),
                        "createdAt", rs.getTimestamp("created_at").toInstant()),
                request.organizationId(),
                nullableTimestamp(request.from()), nullableTimestamp(request.from()),
                nullableTimestamp(request.to()), nullableTimestamp(request.to()),
                exportId);
        for (Map<String, Object> event : events) {
            jdbc.update("""
                            INSERT INTO siem_export_events(siem_export_id, audit_event_id, normalized_event, status)
                            VALUES (?, ?, ?::jsonb, 'EXPORTED')
                            ON CONFLICT DO NOTHING
                            """,
                    exportId, event.get("id"), toJson(event));
        }
        if (!events.isEmpty()) {
            UUID lastEventId = (UUID) events.getLast().get("id");
            jdbc.update("""
                            UPDATE siem_exports
                            SET last_exported_event_id = ?, last_exported_at = now(), status = 'ACTIVE', updated_at = now()
                            WHERE id = ?
                            """,
                    lastEventId, exportId);
        }
        appendSecurityEvent(request.organizationId(), null, null, "AUDIT_EXPORT_COMPLETED", Map.of(
                "sinkType", request.sinkType(),
                "exportedCount", events.size(),
                "siemExportId", exportId.toString()));
    }

    @Override
    @Transactional
    public void recordSignedAction(String signedAdminActionEnvelope) {
        AuthenticatedActor actor = requireActor();
        Map<String, Object> payload = fromJson(signedAdminActionEnvelope);
        UUID orgId = payload.containsKey("organizationId") ? UUID.fromString(String.valueOf(payload.get("organizationId"))) : actor.organizationId();
        requireOrgAccess(actor, orgId);
        SecurityVerifierClient.SignedAdminActionVerificationResponse verification =
                securityVerifierClient.verifySignedAdminAction(new SecurityVerifierClient.SignedAdminActionVerificationRequest(orgId, signedAdminActionEnvelope));
        if (!verification.valid()) {
            throw new SecurityException("Signed admin action verification failed: " + verification.reason());
        }
        String actionType = String.valueOf(payload.getOrDefault("actionType", "SIGNED_ADMIN_ACTION"));
        jdbc.update("""
                        INSERT INTO admin_actions(organization_id, admin_user_id, action_type, signed_action_envelope)
                        VALUES (?, ?, ?, ?::jsonb)
                        """,
                orgId, actor.userId(), actionType, signedAdminActionEnvelope);
        appendSecurityEvent(orgId, actor.userId(), actor.deviceId(), "ADMIN_ACTION_RECORDED", Map.of("actionType", actionType));
    }

    @Override
    public void syncDevicePosture(UUID organizationId) {
        requireOrgAccess(requireActor(), organizationId);
        int compliant = jdbc.update("""
                        UPDATE devices d
                        SET mdm_compliant = true,
                            hardware_backed = COALESCE((m.posture ->> 'hardwareBacked')::boolean, d.hardware_backed),
                            strongbox_or_secure_enclave = COALESCE((m.posture ->> 'secureEnclave')::boolean, d.strongbox_or_secure_enclave),
                            trust_state = CASE WHEN d.revoked_at IS NULL THEN 'VERIFIED' ELSE d.trust_state END,
                            updated_at = now()
                        FROM mdm_devices m
                        WHERE d.id = m.device_id
                          AND m.organization_id = ?
                          AND UPPER(m.compliance_state) IN ('COMPLIANT', 'MANAGED', 'HEALTHY')
                        """,
                organizationId);
        int nonCompliant = jdbc.update("""
                        UPDATE devices d
                        SET mdm_compliant = false,
                            trust_state = CASE WHEN d.revoked_at IS NULL THEN 'QUARANTINED' ELSE d.trust_state END,
                            updated_at = now()
                        FROM mdm_devices m
                        WHERE d.id = m.device_id
                          AND m.organization_id = ?
                          AND UPPER(m.compliance_state) NOT IN ('COMPLIANT', 'MANAGED', 'HEALTHY')
                        """,
                organizationId);
        appendSecurityEvent(organizationId, null, null, "MDM_SYNC_COMPLETED", Map.of(
                "compliantDevices", compliant,
                "nonCompliantDevices", nonCompliant));
    }

    @Override
    @Transactional
    public void exportEvent(String normalizedAuditEventJson) {
        Map<String, Object> normalizedEvent = fromJson(normalizedAuditEventJson);
        assertNoPlaintext(normalizedEvent);
        Object organization = normalizedEvent.get("organizationId");
        if (organization == null) {
            throw new IllegalArgumentException("normalizedAuditEventJson requires organizationId");
        }
        UUID organizationId = UUID.fromString(String.valueOf(organization));
        requireOrgAccess(requireActor(), organizationId);
        UUID exportId = upsertSiemExport(organizationId, String.valueOf(normalizedEvent.getOrDefault("sinkType", "INLINE")));
        jdbc.update("""
                        INSERT INTO siem_export_events(siem_export_id, normalized_event, status)
                        VALUES (?, ?::jsonb, 'EXPORTED')
                        """,
                exportId, toJson(normalizedEvent));
        appendSecurityEvent(organizationId, null, null, "SIEM_EVENT_EXPORTED", Map.of("siemExportId", exportId.toString()));
    }

    @Override
    @Transactional
    public void startLockdown(EmergencyLockdownRequest request) {
        AuthenticatedActor actor = requireActor();
        UUID startedBy = actor.userId();
        if (startedBy == null) {
            startedBy = firstUserInOrg(request.organizationId());
        }
        jdbc.update("""
                        INSERT INTO emergency_lockdowns(organization_id, room_id, scope, reason, started_by)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                request.organizationId(), request.roomId(), request.scope(), request.reason(), startedBy);
        if (request.roomId() != null) {
            jdbc.update("UPDATE rooms SET lockdown_state = 'LOCKED_DOWN' WHERE id = ?", request.roomId());
        }
        appendSecurityEvent(request.organizationId(), actor.userId(), actor.deviceId(), "LOCKDOWN_STARTED", Map.of("scope", request.scope(), "reason", request.reason()));
    }

    @Override
    @Transactional
    public void endLockdown(UUID lockdownId, String reason) {
        AuthenticatedActor actor = requireActor();
        UUID orgId = jdbc.query("SELECT organization_id FROM emergency_lockdowns WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("Lockdown not found");
                    }
                    return rs.getObject("organization_id", UUID.class);
                },
                lockdownId);
        jdbc.update("UPDATE emergency_lockdowns SET ended_by = ?, ended_at = now() WHERE id = ?", actor.userId(), lockdownId);
        jdbc.update("""
                        UPDATE rooms SET lockdown_state = 'NORMAL'
                        WHERE id IN (
                            SELECT room_id FROM emergency_lockdowns WHERE id = ? AND room_id IS NOT NULL
                        )
                        """,
                lockdownId);
        appendSecurityEvent(orgId, actor.userId(), actor.deviceId(), "LOCKDOWN_ENDED", Map.of("lockdownId", lockdownId.toString(), "reason", reason));
    }

    private WebAuthnStartResponse storeWebAuthnChallenge(UUID userId, String ceremonyType, String challenge, Map<String, Object> options) {
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        jdbc.update("""
                        INSERT INTO webauthn_challenges(user_id, ceremony_type, challenge_hash, public_challenge, request_options_json, expires_at)
                        VALUES (?, ?, ?, ?, ?::jsonb, ?)
                        """,
                userId, ceremonyType, tokenService.sha256(challenge), challenge, toJson(options), Timestamp.from(expiresAt));
        return new WebAuthnStartResponse(challenge, options);
    }

    private WebAuthnChallenge consumeChallenge(UUID userId, String ceremonyType) {
        WebAuthnChallenge challenge = jdbc.query("""
                        SELECT public_challenge, request_options_json::text
                        FROM webauthn_challenges
                        WHERE user_id = ?
                          AND ceremony_type = ?
                          AND consumed_at IS NULL
                          AND expires_at > now()
                          AND request_options_json IS NOT NULL
                        ORDER BY created_at DESC
                        LIMIT 1
                        FOR UPDATE
                        """,
                rs -> rs.next() ? new WebAuthnChallenge(rs.getString("public_challenge"), rs.getString("request_options_json")) : null,
                userId, ceremonyType);
        if (challenge == null) {
            throw new SecurityException("No active WebAuthn challenge or challenge already consumed");
        }
        int updated = jdbc.update("""
                        UPDATE webauthn_challenges
                        SET consumed_at = now()
                        WHERE user_id = ?
                          AND ceremony_type = ?
                          AND public_challenge = ?
                          AND consumed_at IS NULL
                        """,
                userId, ceremonyType, challenge.publicChallenge());
        if (updated != 1) {
            throw new SecurityException("WebAuthn challenge was already consumed");
        }
        return challenge;
    }

    private RelyingParty relyingParty() {
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(webauthnRpId)
                        .name(webauthnRpName)
                        .build())
                .credentialRepository(new JdbcCredentialRepository())
                .origins(allowedOriginSet())
                .attestationConveyancePreference(AttestationConveyancePreference.DIRECT)
                .allowUntrustedAttestation(true)
                .validateSignatureCounter(true)
                .build();
    }

    private Set<String> allowedOriginSet() {
        Set<String> origins = new LinkedHashSet<>();
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(origins::add);
        if (origins.isEmpty()) {
            origins.add("http://localhost:8080");
        }
        return origins;
    }

    private UserIdentity userIdentity(UUID userId) {
        Map<String, Object> user = jdbc.query("""
                        SELECT email::text AS email, display_name
                        FROM users
                        WHERE id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("User not found");
                    }
                    return Map.of("email", rs.getString("email"), "displayName", rs.getString("display_name"));
                },
                userId);
        return UserIdentity.builder()
                .name(String.valueOf(user.get("email")))
                .displayName(String.valueOf(user.get("displayName")))
                .id(userHandle(userId))
                .build();
    }

    private ByteArray userHandle(UUID userId) {
        return new ByteArray(userId.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> toYubicoMap(Object value) {
        try {
            String json;
            if (value instanceof PublicKeyCredentialCreationOptions options) {
                json = options.toJson();
            } else if (value instanceof AssertionRequest request) {
                json = request.toJson();
            } else {
                json = objectMapper.writeValueAsString(value);
            }
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("WebAuthn options cannot be serialized", e);
        }
    }

    private void requireWebAuthnRegistrationActor(UUID userId) {
        AuthenticatedActor actor = requireActor();
        UUID orgId = organizationForUser(userId);
        requireOrgAccess(actor, orgId);
        if (actor.bootstrap() || actor.userId().equals(userId) || hasOrgAdminRole(actor)) {
            return;
        }
        throw new SecurityException("Passkey registration requires bootstrap, same-user session, or org-admin role");
    }

    private SessionResponse issueSession(UUID userId, UUID deviceId) {
        if (deviceId == null) {
            throw new SecurityException("Session issuance requires an enrolled device");
        }
        if (!userId.equals(userForDevice(deviceId))) {
            throw new SecurityException("Device does not belong to requested user");
        }
        if (requireVerifiedDevicesForSessions) {
            String trustState = jdbc.query("SELECT trust_state FROM devices WHERE id = ? AND revoked_at IS NULL",
                    rs -> rs.next() ? rs.getString("trust_state") : null,
                    deviceId);
            if (!"VERIFIED".equals(trustState)) {
                throw new SecurityException("Verified device is required for session issuance");
            }
        }
        String token = tokenService.newToken();
        Instant expiresAt = Instant.now().plus(12, ChronoUnit.HOURS);
        jdbc.update("""
                        INSERT INTO api_sessions(user_id, device_id, token_hash, expires_at)
                        VALUES (?, ?, ?, ?)
                        """,
                userId, deviceId, tokenService.sessionTokenHash(token), Timestamp.from(expiresAt));
        return new SessionResponse(userId, deviceId, token, expiresAt);
    }

    private final class JdbcCredentialRepository implements CredentialRepository {
        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            UUID userId = parseUserHandle(username);
            return jdbc.query("""
                            SELECT credential_id
                            FROM webauthn_credentials
                            WHERE user_id = ?
                              AND verification_status = 'VERIFIED'
                            """,
                    (rs, rowNum) -> PublicKeyCredentialDescriptor.builder()
                            .id(new ByteArray(rs.getBytes("credential_id")))
                            .type(PublicKeyCredentialType.PUBLIC_KEY)
                            .build(),
                    userId).stream().collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            UUID userId = parseUserHandle(username);
            requireUserExists(userId);
            return Optional.of(userHandle(userId));
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            UUID userId = parseUserHandle(new String(userHandle.getBytes(), StandardCharsets.UTF_8));
            requireUserExists(userId);
            return Optional.of(userId.toString());
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            UUID userId = parseUserHandle(new String(userHandle.getBytes(), StandardCharsets.UTF_8));
            return credential(credentialId, userId);
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            return jdbc.query("""
                            SELECT credential_id, user_id, public_key_cose, signature_count, backup_eligible, backup_state
                            FROM webauthn_credentials
                            WHERE credential_id = ?
                              AND verification_status = 'VERIFIED'
                            """,
                    (rs, rowNum) -> registeredCredential(rs.getBytes("credential_id"),
                            rs.getObject("user_id", UUID.class),
                            rs.getBytes("public_key_cose"),
                            rs.getLong("signature_count"),
                            rs.getBoolean("backup_eligible"),
                            rs.getBoolean("backup_state")),
                    credentialId.getBytes()).stream().collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        private Optional<RegisteredCredential> credential(ByteArray credentialId, UUID userId) {
            return jdbc.query("""
                            SELECT credential_id, user_id, public_key_cose, signature_count, backup_eligible, backup_state
                            FROM webauthn_credentials
                            WHERE credential_id = ?
                              AND user_id = ?
                              AND verification_status = 'VERIFIED'
                            """,
                    rs -> {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(registeredCredential(rs.getBytes("credential_id"),
                                rs.getObject("user_id", UUID.class),
                                rs.getBytes("public_key_cose"),
                                rs.getLong("signature_count"),
                                rs.getBoolean("backup_eligible"),
                                rs.getBoolean("backup_state")));
                    },
                    credentialId.getBytes(), userId);
        }

        private RegisteredCredential registeredCredential(byte[] credentialId, UUID userId, byte[] publicKeyCose,
                                                          long signatureCount, boolean backupEligible, boolean backupState) {
            return RegisteredCredential.builder()
                    .credentialId(new ByteArray(credentialId))
                    .userHandle(userHandle(userId))
                    .publicKeyCose(new ByteArray(publicKeyCose))
                    .signatureCount(signatureCount)
                    .backupEligible(backupEligible)
                    .backupState(backupState)
                    .build();
        }

        private UUID parseUserHandle(String value) {
            try {
                return UUID.fromString(value);
            } catch (RuntimeException e) {
                throw new SecurityException("Invalid WebAuthn user handle", e);
            }
        }
    }

    private record WebAuthnChallenge(String publicChallenge, String requestOptionsJson) {
    }

    private void uploadPrekey(String table, PreKeyUploadRequest request, boolean requiresSignature, boolean hasExpiry) {
        organizationForDevice(request.deviceId());
        requireDeviceActor(request.deviceId());
        byte[] publicKey = decodeBase64(request.publicKeyBase64(), "publicKeyBase64");
        byte[] signature = request.signatureBase64() == null || request.signatureBase64().isBlank()
                ? new byte[0]
                : decodeBase64(request.signatureBase64(), "signatureBase64");
        if (requiresSignature && signature.length == 0) {
            throw new IllegalArgumentException(table + " requires signatureBase64");
        }
        if ("signed_prekeys".equals(table)) {
            jdbc.update("""
                            INSERT INTO signed_prekeys(device_id, key_id, algorithm, public_key, signature, expires_at)
                            VALUES (?, ?, ?, ?, ?, now() + interval '30 days')
                            ON CONFLICT (device_id, key_id) DO UPDATE SET public_key = EXCLUDED.public_key, signature = EXCLUDED.signature, expires_at = EXCLUDED.expires_at
                            """,
                    request.deviceId(), request.keyId(), request.algorithm(), publicKey, signature);
        } else if ("one_time_prekeys".equals(table)) {
            jdbc.update("""
                            INSERT INTO one_time_prekeys(device_id, key_id, algorithm, public_key)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (device_id, key_id) DO NOTHING
                            """,
                    request.deviceId(), request.keyId(), request.algorithm(), publicKey);
        } else {
            jdbc.update("""
                            INSERT INTO pq_prekeys(device_id, key_id, algorithm, public_key, signature, expires_at)
                            VALUES (?, ?, ?, ?, ?, now() + interval '30 days')
                            ON CONFLICT (device_id, key_id) DO UPDATE SET public_key = EXCLUDED.public_key, signature = EXCLUDED.signature, expires_at = EXCLUDED.expires_at
                            """,
                    request.deviceId(), request.keyId(), request.algorithm(), publicKey, signature);
        }
    }

    private List<Map<String, Object>> prekeys(UUID userId, String table, String type) {
        String signatureProjection = "one_time_prekeys".equals(table) ? "NULL::bytea AS signature" : "p.signature";
        String claimedFilter = "one_time_prekeys".equals(table) || "pq_prekeys".equals(table) ? " AND p.claimed_at IS NULL" : "";
        String sql = """
                SELECT d.id AS device_id, p.key_id, p.algorithm, p.public_key, %s, p.created_at
                FROM devices d
                JOIN %s p ON p.device_id = d.id
                WHERE d.user_id = ? AND d.revoked_at IS NULL %s
                ORDER BY p.created_at DESC
                LIMIT 50
                """.formatted(signatureProjection, table, claimedFilter);
        List<Map<String, Object>> rows = jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type);
            map.put("deviceId", rs.getObject("device_id", UUID.class).toString());
            map.put("keyId", rs.getString("key_id"));
            map.put("algorithm", rs.getString("algorithm"));
            map.put("publicKeyBase64", Base64.getEncoder().encodeToString(rs.getBytes("public_key")));
            byte[] signature = rs.getBytes("signature");
            map.put("signatureBase64", signature == null ? null : Base64.getEncoder().encodeToString(signature));
            map.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
            return map;
        }, userId);
        if ("one_time_prekeys".equals(table) || "pq_prekeys".equals(table)) {
            for (Map<String, Object> row : rows) {
                jdbc.update("UPDATE " + table + " SET claimed_at = COALESCE(claimed_at, now()) WHERE device_id = ? AND key_id = ?",
                        UUID.fromString(String.valueOf(row.get("deviceId"))), row.get("keyId"));
            }
        }
        return rows;
    }

    private EncryptedMessageResponse messageResponse(ResultSet rs) throws SQLException {
        return new EncryptedMessageResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("room_id", UUID.class),
                rs.getObject("sender_user_id", UUID.class),
                rs.getObject("sender_device_id", UUID.class),
                rs.getObject("recipient_user_id", UUID.class),
                rs.getObject("recipient_device_id", UUID.class),
                rs.getString("message_kind"),
                Base64.getEncoder().encodeToString(rs.getBytes("ciphertext")),
                Base64.getEncoder().encodeToString(rs.getBytes("ciphertext_sha256")),
                fromJson(rs.getString("crypto_metadata")),
                rs.getTimestamp("server_received_at").toInstant());
    }

    private IdResponse returning(String sql, Object... args) {
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                throw new IllegalStateException("Insert did not return an id");
            }
            return new IdResponse(rs.getObject("id", UUID.class), rs.getTimestamp("created_at").toInstant());
        }, args);
    }

    private void appendSecurityEventForUser(UUID userId, String eventType, Map<String, Object> metadata) {
        appendSecurityEvent(organizationForUser(userId), userId, null, eventType, metadata);
    }

    private void appendSecurityEvent(UUID organizationId, UUID actorUserId, UUID actorDeviceId, String eventType, Map<String, Object> metadata) {
        byte[] previous = latestHash("audit_events", "event_hash", "organization_id", organizationId);
        String metadataJson = toJson(nullToEmpty(metadata));
        byte[] eventHash = tokenService.sha256(concat(
                previous,
                organizationId.toString().getBytes(StandardCharsets.UTF_8),
                eventType.getBytes(StandardCharsets.UTF_8),
                metadataJson.getBytes(StandardCharsets.UTF_8)));
        jdbc.update("""
                        INSERT INTO audit_events(organization_id, actor_user_id, actor_device_id, event_type, metadata, previous_event_hash, event_hash)
                        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                        """,
                organizationId, actorUserId, actorDeviceId, eventType, metadataJson, previous, eventHash);
    }

    private byte[] latestHash(String table, String column, String orgColumn, UUID organizationId) {
        String sql = "SELECT " + column + " FROM " + table + " WHERE " + orgColumn + " = ? ORDER BY created_at DESC LIMIT 1";
        return jdbc.query(sql, rs -> rs.next() ? rs.getBytes(column) : null, organizationId);
    }

    private void assertNoPlaintext(Map<String, Object> metadata) {
        plaintextGuard.rejectPlaintextShapedMetadata(metadata);
    }

    private void enforceNoActiveLockdown(UUID organizationId, UUID roomId) {
        Integer count = jdbc.queryForObject("""
                        SELECT count(*)
                        FROM emergency_lockdowns
                        WHERE organization_id = ?
                          AND ended_at IS NULL
                          AND (room_id IS NULL OR room_id = ? OR ? IS NULL)
                        """,
                Integer.class,
                organizationId, roomId, roomId);
        if (count != null && count > 0) {
            throw new SecurityException("Organization or room is under active lockdown");
        }
    }

    private void requireDeviceActor(UUID deviceId) {
        AuthenticatedActor actor = requireActor();
        UUID orgId = organizationForDevice(deviceId);
        requireOrgAccess(actor, orgId);
        if (!actor.bootstrap() && !deviceId.equals(actor.deviceId())) {
            throw new SecurityException("Device-scoped operation must match authenticated session");
        }
    }

    private void requireDeviceActorOrOrgAdmin(UUID deviceId, UUID orgId) {
        AuthenticatedActor actor = requireActor();
        requireOrgAccess(actor, orgId);
        if (actor.bootstrap() || hasOrgAdminRole(actor) || deviceId.equals(actor.deviceId())) {
            return;
        }
        throw new SecurityException("Device owner or admin role is required");
    }

    private void requireRoomAdmin(UUID roomId, UUID organizationId) {
        AuthenticatedActor actor = requireActor();
        requireOrgAccess(actor, organizationId);
        if (actor.bootstrap()) {
            throw new SecurityException("Bearer session is required for room administration");
        }
        if (hasOrgAdminRole(actor)) {
            return;
        }
        Integer owner = jdbc.queryForObject("""
                        SELECT count(*)
                        FROM room_members
                        WHERE room_id = ?
                          AND user_id = ?
                          AND role = 'OWNER'
                          AND membership_state = 'ACTIVE'
                        """,
                Integer.class,
                roomId, actor.userId());
        if (owner == null || owner == 0) {
            throw new SecurityException("Room owner or admin role is required");
        }
    }

    private void requireRoomMember(AuthenticatedActor actor, UUID roomId) {
        if (roomId == null || actor.bootstrap() || hasOrgAdminRole(actor)) {
            return;
        }
        Integer member = jdbc.queryForObject("""
                        SELECT count(*)
                        FROM room_members
                        WHERE room_id = ?
                          AND user_id = ?
                          AND membership_state = 'ACTIVE'
                        """,
                Integer.class,
                roomId, actor.userId());
        if (member == null || member == 0) {
            throw new SecurityException("Room membership is required");
        }
    }

    private void requireMessageVisibleToDevice(UUID messageId, UUID deviceId) {
        Integer visible = jdbc.queryForObject("""
                        SELECT count(*)
                        FROM encrypted_messages m
                        WHERE m.id = ?
                          AND m.deleted_at IS NULL
                          AND (
                              m.recipient_device_id = ?
                              OR m.sender_device_id = ?
                              OR m.room_id IN (
                                  SELECT rm.room_id
                                  FROM room_members rm
                                  JOIN devices d ON d.user_id = rm.user_id
                                  WHERE d.id = ? AND rm.membership_state = 'ACTIVE'
                              )
                          )
                        """,
                Integer.class,
                messageId, deviceId, deviceId, deviceId);
        if (visible == null || visible == 0) {
            throw new SecurityException("Receipt device cannot access message");
        }
    }

    private boolean hasOrgAdminRole(AuthenticatedActor actor) {
        return actor.hasRole("ADMIN") || actor.hasRole("ORG_ADMIN") || actor.hasRole("PLATFORM_OPERATOR");
    }

    private UUID upsertSiemExport(UUID organizationId, String sinkType) {
        return jdbc.query("""
                        INSERT INTO siem_exports(organization_id, sink_type, sink_config_ref, status)
                        VALUES (?, ?, 'database-backed-export-buffer', 'ACTIVE')
                        ON CONFLICT (organization_id, sink_type)
                        DO UPDATE SET status = 'ACTIVE', updated_at = now()
                        RETURNING id
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new IllegalStateException("SIEM export upsert did not return an id");
                    }
                    return rs.getObject("id", UUID.class);
                },
                organizationId, sinkType);
    }

    private Timestamp nullableTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private UUID organizationForUser(UUID userId) {
        return jdbc.query("SELECT organization_id FROM users WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("User not found");
                    }
                    return rs.getObject("organization_id", UUID.class);
                },
                userId);
    }

    private UUID organizationForDevice(UUID deviceId) {
        return jdbc.query("SELECT organization_id FROM devices WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("Device not found");
                    }
                    return rs.getObject("organization_id", UUID.class);
                },
                deviceId);
    }

    private UUID organizationForRoom(UUID roomId) {
        return jdbc.query("SELECT organization_id FROM rooms WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("Room not found");
                    }
                    return rs.getObject("organization_id", UUID.class);
                },
                roomId);
    }

    private UUID userForDevice(UUID deviceId) {
        return jdbc.query("SELECT user_id FROM devices WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("Device not found");
                    }
                    return rs.getObject("user_id", UUID.class);
                },
                deviceId);
    }

    private UUID latestDeviceForUser(UUID userId) {
        return jdbc.query("SELECT id FROM devices WHERE user_id = ? AND revoked_at IS NULL ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                userId);
    }

    private UUID firstUserInOrg(UUID organizationId) {
        return jdbc.query("SELECT id FROM users WHERE organization_id = ? ORDER BY created_at LIMIT 1",
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("Organization has no users");
                    }
                    return rs.getObject("id", UUID.class);
                },
                organizationId);
    }

    private void requireUserExists(UUID userId) {
        organizationForUser(userId);
    }

    private AuthenticatedActor requireActor() {
        AuthenticatedActor actor = actorOrNull();
        if (actor == null) {
            throw new SecurityException("Authenticated actor required");
        }
        return actor;
    }

    private AuthenticatedActor actorOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            return null;
        }
        return actor;
    }

    private void requireOrgAccess(AuthenticatedActor actor, UUID organizationId) {
        if (!actor.bootstrap() && !organizationId.equals(actor.organizationId())) {
            throw new SecurityException("Actor cannot access another organization");
        }
    }

    private String normalizeRole(String role) {
        String normalized = role == null || role.isBlank() ? "MEMBER" : role.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported role: " + role);
        }
        return normalized;
    }

    private Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private byte[] decodeBase64(String value, String fieldName) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getUrlDecoder().decode(value);
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException(fieldName + " must be base64 encoded");
            }
        }
    }

    private byte[] decodeFlexibleHash(String value, String fieldName) {
        byte[] decoded;
        if (value.matches("(?i)[0-9a-f]{64}")) {
            byte[] bytes = new byte[32];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
            }
            decoded = bytes;
        } else {
            decoded = decodeBase64(value, fieldName);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException(fieldName + " must be a SHA-256 value");
        }
        return decoded;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Value cannot be serialized to JSON", e);
        }
    }

    private Map<String, Object> fromJson(String value) {
        try {
            if (value == null || value.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    private byte[] concat(byte[]... values) {
        int size = 0;
        for (byte[] value : values) {
            if (value != null) {
                size += value.length;
            }
        }
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] value : values) {
            if (value == null) {
                continue;
            }
            System.arraycopy(value, 0, out, offset, value.length);
            offset += value.length;
        }
        return out;
    }

    private byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }
}
