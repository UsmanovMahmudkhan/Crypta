package com.sovereigncomm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommonDtos {
    private CommonDtos() {
    }

    public record IdResponse(UUID id, Instant createdAt) {
    }

    public record ErrorResponse(Instant timestamp, int status, String error, String message, String path, String requestId) {
    }

    public record OrganizationCreateRequest(@NotBlank String name, @NotBlank String jurisdiction, String externalTenantId) {
    }

    public record UserRegisterRequest(@NotNull UUID organizationId, @NotBlank String email, @NotBlank String displayName, List<String> roles) {
    }

    public record DeviceRegisterRequest(
            @NotNull UUID userId,
            @NotBlank String platform,
            @NotBlank String deviceName,
            @NotBlank String attestationFormat,
            @NotBlank String attestationObjectBase64,
            @NotBlank String deviceSigningPublicKeyBase64) {
    }

    public record WebAuthnStartResponse(@NotBlank String challengeBase64, Map<String, Object> publicKeyCredentialOptions) {
    }

    public record WebAuthnFinishRequest(@NotNull UUID userId, UUID deviceId, @NotBlank String credentialJson) {
    }

    public record BootstrapSessionRequest(@NotNull UUID userId, @NotNull UUID deviceId) {
    }

    public record SessionResponse(UUID userId, UUID deviceId, String token, Instant expiresAt) {
    }

    public record PublicKeyUploadRequest(@NotNull UUID deviceId, @NotBlank String algorithm, @NotBlank String publicKeyBase64, @NotBlank String signatureBase64) {
    }

    public record PreKeyUploadRequest(@NotNull UUID deviceId, @NotBlank String keyId, @NotBlank String algorithm, @NotBlank String publicKeyBase64, String signatureBase64) {
    }

    public record KeyBundleResponse(UUID userId, UUID deviceId, List<Map<String, Object>> identityKeys, List<Map<String, Object>> prekeys,
                                    Map<String, Object> transparencyProof) {
    }

    public record KeyTransparencyProofResponse(UUID userId, long latestLogIndex, String latestSignedTreeHeadBase64,
                                               String consistencyProofBase64, String checkpointId, String proofVersion,
                                               List<Map<String, Object>> entries) {
    }

    public record RoomCreateRequest(@NotNull UUID organizationId, @NotBlank String name, @NotBlank String classification, Map<String, Object> policy) {
    }

    public record RoomPolicyUpdateRequest(@NotNull UUID roomId, Map<String, Object> policy, @NotBlank String reason) {
    }

    public record MemberChangeRequest(@NotNull UUID roomId, @NotNull UUID userId, List<UUID> allowedDeviceIds, @NotBlank String reason) {
    }

    public record AttachmentCreateRequest(@NotNull UUID roomId, @NotBlank String objectKey, @NotBlank String ciphertextSha256, long ciphertextBytes, Map<String, Object> cryptoMetadata) {
    }

    public record EncryptedMessageSubmitRequest(
            @NotNull UUID organizationId,
            UUID roomId,
            @NotNull UUID senderUserId,
            @NotNull UUID senderDeviceId,
            UUID recipientUserId,
            UUID recipientDeviceId,
            @NotBlank String messageKind,
            @NotBlank String ciphertextBase64,
            @NotBlank String ciphertextSha256,
            Map<String, Object> cryptoMetadata) {
    }

    public record EncryptedMessageResponse(
            UUID id,
            UUID organizationId,
            UUID roomId,
            UUID senderUserId,
            UUID senderDeviceId,
            UUID recipientUserId,
            UUID recipientDeviceId,
            String messageKind,
            String ciphertextBase64,
            String ciphertextSha256,
            Map<String, Object> cryptoMetadata,
            Instant serverReceivedAt) {
    }

    public record DeliveryReceiptRequest(@NotNull UUID messageId, @NotNull UUID deviceId, @NotBlank String receiptType) {
    }

    public record MessageInboxRequest(@NotNull UUID deviceId, Instant after, @Positive Integer limit) {
    }

    public record AuditExportRequest(@NotNull UUID organizationId, @NotBlank String sinkType, Instant from, Instant to) {
    }

    public record EmergencyLockdownRequest(@NotNull UUID organizationId, UUID roomId, @NotBlank String reason, @NotBlank String scope) {
    }
}
