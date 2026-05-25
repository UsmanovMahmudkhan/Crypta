package com.sovereigncomm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereigncomm.api.dto.CommonDtos.*;
import com.sovereigncomm.config.AuthenticatedActor;
import com.sovereigncomm.security.PlaintextGuard;
import com.sovereigncomm.security.TokenService;
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

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final PlaintextGuard plaintextGuard;
    private final String webauthnRpId;
    private final String webauthnRpName;
    private final String allowedOrigins;

    JdbcSovereignCommServices(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            TokenService tokenService,
            PlaintextGuard plaintextGuard,
            @Value("${security.webauthn.rp-id:localhost}") String webauthnRpId,
            @Value("${security.webauthn.rp-name:Sovereign Comm}") String webauthnRpName,
            @Value("${security.webauthn.allowed-origins:http://localhost:8080}") String allowedOrigins) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.plaintextGuard = plaintextGuard;
        this.webauthnRpId = webauthnRpId;
        this.webauthnRpName = webauthnRpName;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    @Transactional
    public WebAuthnStartResponse startWebAuthnRegistration(UUID userId) {
        requireUserExists(userId);
        return startWebAuthn(userId, "REGISTRATION");
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
        throw new SecurityException("WebAuthn credential verification is not configured");
    }

    @Override
    @Transactional
    public WebAuthnStartResponse startWebAuthnLogin(UUID userId) {
        requireUserExists(userId);
        Integer credentials = jdbc.queryForObject("SELECT count(*) FROM webauthn_credentials WHERE user_id = ?", Integer.class, userId);
        if (credentials == null || credentials == 0) {
            throw new SecurityException("User has no registered passkey");
        }
        return startWebAuthn(userId, "LOGIN");
    }

    @Override
    @Transactional
    public SessionResponse finishWebAuthnLogin(WebAuthnFinishRequest request) {
        throw new SecurityException("WebAuthn credential verification is not configured");
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
        byte[] signingKey = decodeBase64(request.deviceSigningPublicKeyBase64(), "deviceSigningPublicKeyBase64");
        byte[] attestation = decodeBase64(request.attestationObjectBase64(), "attestationObjectBase64");
        IdResponse response = returning("""
                        INSERT INTO devices(user_id, organization_id, platform, device_name, device_signing_public_key)
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id, created_at
                        """,
                request.userId(), orgId, request.platform(), request.deviceName(), signingKey);
        jdbc.update("""
                        INSERT INTO device_attestations(device_id, attestation_format, attestation_statement, verification_status, verified_claims)
                        VALUES (?, ?, ?, 'PENDING_EXTERNAL_VERIFICATION', '{}'::jsonb)
                        """,
                response.id(), request.attestationFormat(), attestation);
        appendSecurityEvent(orgId, request.userId(), response.id(), "DEVICE_REGISTERED", Map.of("platform", request.platform()));
        return response;
    }

    @Override
    @Transactional
    public void revokeDevice(UUID deviceId, String reason) {
        UUID orgId = organizationForDevice(deviceId);
        jdbc.update("UPDATE devices SET trust_state = 'REVOKED', revoked_at = now(), revoke_reason = ? WHERE id = ?", reason, deviceId);
        jdbc.update("UPDATE api_sessions SET revoked_at = now() WHERE device_id = ?", deviceId);
        appendSecurityEvent(orgId, null, deviceId, "DEVICE_REVOKED", Map.of("reason", reason));
    }

    @Override
    @Transactional
    public void uploadIdentityKey(PublicKeyUploadRequest request) {
        UUID orgId = organizationForDevice(request.deviceId());
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
        return new KeyBundleResponse(userId, firstDevice, identityKeys, prekeys);
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
        byte[] previousHash = latestHash("key_transparency_entries", "merkle_leaf_hash", "organization_id", orgId);
        long logIndex = Optional.ofNullable(jdbc.queryForObject("SELECT COALESCE(MAX(log_index), -1) + 1 FROM key_transparency_entries", Long.class)).orElse(0L);
        byte[] leafHash = tokenService.sha256(concat(previousHash, canonicalHash, signatureHash, longBytes(logIndex)));
        jdbc.update("""
                        INSERT INTO key_transparency_entries(
                            organization_id, subject_user_id, subject_device_id, entry_type, canonical_entry_hash,
                            previous_entry_hash, merkle_leaf_hash, log_index, signed_tree_head, inclusion_proof)
                        VALUES (?, ?, ?, 'IDENTITY_KEY', ?, ?, ?, ?, ?, ?::jsonb)
                        """,
                orgId, userId, deviceId, canonicalHash, previousHash, leafHash, logIndex, leafHash,
                toJson(Map.of("mode", "append_only_hash_chain", "externalAuditorRequired", true)));
    }

    @Override
    public Map<String, Object> proof(UUID userId) {
        KeyTransparencyProofResponse response = proofResponse(userId);
        return Map.of(
                "userId", response.userId(),
                "latestLogIndex", response.latestLogIndex(),
                "latestSignedTreeHeadBase64", response.latestSignedTreeHeadBase64(),
                "entries", response.entries());
    }

    @Override
    public KeyTransparencyProofResponse proofResponse(UUID userId) {
        List<Map<String, Object>> entries = jdbc.query("""
                        SELECT log_index, canonical_entry_hash, previous_entry_hash, merkle_leaf_hash, signed_tree_head, created_at
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
                    entry.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    return entry;
                },
                userId);
        long latestIndex = entries.isEmpty() ? -1L : ((Number) entries.get(entries.size() - 1).get("logIndex")).longValue();
        String latestSth = entries.isEmpty() ? "" : String.valueOf(entries.get(entries.size() - 1).get("signedTreeHeadBase64"));
        return new KeyTransparencyProofResponse(userId, latestIndex, latestSth, entries);
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
                        SELECT object_key, ciphertext_sha256, ciphertext_bytes, crypto_metadata::text
                        FROM encrypted_attachments
                        WHERE id = ? AND deleted_at IS NULL
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new NoSuchElementException("Attachment not found");
                    }
                    return Map.of(
                            "attachmentId", attachmentId,
                            "objectKey", rs.getString("object_key"),
                            "ciphertextSha256", Base64.getEncoder().encodeToString(rs.getBytes("ciphertext_sha256")),
                            "ciphertextBytes", rs.getLong("ciphertext_bytes"),
                            "cryptoMetadata", fromJson(rs.getString("crypto_metadata")),
                            "downloadMode", "OBJECT_STORAGE_SIGNING_REQUIRED");
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
        jdbc.update("""
                        INSERT INTO siem_exports(organization_id, sink_type, sink_config_ref, status)
                        VALUES (?, ?, 'configured-out-of-band', 'ACTIVE')
                        """,
                request.organizationId(), request.sinkType());
        appendSecurityEvent(request.organizationId(), null, null, "AUDIT_EXPORT_REQUESTED", Map.of("sinkType", request.sinkType()));
    }

    @Override
    @Transactional
    public void recordSignedAction(String signedAdminActionEnvelope) {
        AuthenticatedActor actor = requireActor();
        Map<String, Object> payload = fromJson(signedAdminActionEnvelope);
        UUID orgId = payload.containsKey("organizationId") ? UUID.fromString(String.valueOf(payload.get("organizationId"))) : actor.organizationId();
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
        appendSecurityEvent(organizationId, null, null, "MDM_SYNC_REQUESTED", Map.of("status", "connector_required"));
    }

    @Override
    public void exportEvent(String normalizedAuditEventJson) {
        assertNoPlaintext(fromJson(normalizedAuditEventJson));
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

    private WebAuthnStartResponse startWebAuthn(UUID userId, String ceremonyType) {
        String challenge = tokenService.newChallenge();
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        jdbc.update("""
                        INSERT INTO webauthn_challenges(user_id, ceremony_type, challenge_hash, public_challenge, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                userId, ceremonyType, tokenService.sha256(challenge), challenge, Timestamp.from(expiresAt));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", challenge);
        options.put("rp", Map.of("id", webauthnRpId, "name", webauthnRpName));
        options.put("allowedOrigins", Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
        options.put("user", Map.of("id", userId.toString()));
        options.put("timeout", 300000);
        options.put("userVerification", "required");
        options.put("attestation", "direct");
        options.put("verificationMode", "challenge_replay_protected_until_yubico_finish_verification_is_wired");
        return new WebAuthnStartResponse(challenge, options);
    }

    private void consumeChallenge(UUID userId, String ceremonyType) {
        int updated = jdbc.update("""
                        UPDATE webauthn_challenges
                        SET consumed_at = now()
                        WHERE id = (
                            SELECT id
                            FROM webauthn_challenges
                            WHERE user_id = ?
                              AND ceremony_type = ?
                              AND consumed_at IS NULL
                              AND expires_at > now()
                            ORDER BY created_at DESC
                            LIMIT 1
                            FOR UPDATE
                        )
                        """,
                userId, ceremonyType);
        if (updated != 1) {
            throw new SecurityException("No active WebAuthn challenge or challenge already consumed");
        }
    }

    private SessionResponse issueSession(UUID userId, UUID deviceId) {
        String token = tokenService.newToken();
        Instant expiresAt = Instant.now().plus(12, ChronoUnit.HOURS);
        jdbc.update("""
                        INSERT INTO api_sessions(user_id, device_id, token_hash, expires_at)
                        VALUES (?, ?, ?, ?)
                        """,
                userId, deviceId, tokenService.sha256(token), Timestamp.from(expiresAt));
        return new SessionResponse(userId, deviceId, token, expiresAt);
    }

    private void uploadPrekey(String table, PreKeyUploadRequest request, boolean requiresSignature, boolean hasExpiry) {
        organizationForDevice(request.deviceId());
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
        return jdbc.query(sql, (rs, rowNum) -> {
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
        return role == null || role.isBlank() ? "MEMBER" : role.trim().toUpperCase(Locale.ROOT).replace('-', '_');
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
        if (value.matches("(?i)[0-9a-f]{64}")) {
            byte[] bytes = new byte[32];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
            }
            return bytes;
        }
        return decodeBase64(value, fieldName);
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
