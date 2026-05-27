package com.sovereigncomm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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

    public record OrganizationCreateRequest(@NotBlank @Size(max = 160) String name,
                                             @NotBlank @Size(max = 80) String jurisdiction,
                                             @Size(max = 160) String externalTenantId) {
    }

    public record UserRegisterRequest(@NotNull UUID organizationId,
                                      @NotBlank @Size(max = 320) String email,
                                      @NotBlank @Size(max = 160) String displayName,
                                      @Size(max = 8) List<@Pattern(regexp = "MEMBER|ADMIN|ORG_ADMIN|PLATFORM_OPERATOR", flags = Pattern.Flag.CASE_INSENSITIVE) String> roles) {
    }

    public record DeviceRegisterRequest(
            @NotNull UUID userId,
            @NotBlank @Size(max = 40) String platform,
            @NotBlank @Size(max = 160) String deviceName,
            @NotBlank @Size(max = 80) String attestationFormat,
            @NotBlank @Size(max = 20000) String attestationObjectBase64,
            @NotBlank @Size(max = 12000) String deviceSigningPublicKeyBase64) {
    }

    public record WebAuthnStartResponse(@NotBlank String challengeBase64, Map<String, Object> publicKeyCredentialOptions) {
    }

    public record WebAuthnFinishRequest(@NotNull UUID userId, UUID deviceId, @NotBlank @Size(max = 20000) String credentialJson) {
    }

    public record BootstrapSessionRequest(@NotNull UUID userId, @NotNull UUID deviceId) {
    }

    public record SessionResponse(UUID userId, UUID deviceId, String token, Instant expiresAt) {
    }

    public record PublicKeyUploadRequest(@NotNull UUID deviceId,
                                         @NotBlank @Size(max = 80) String algorithm,
                                         @NotBlank @Size(max = 12000) String publicKeyBase64,
                                         @NotBlank @Size(max = 12000) String signatureBase64) {
    }

    public record PreKeyUploadRequest(@NotNull UUID deviceId,
                                      @NotBlank @Size(max = 160) String keyId,
                                      @NotBlank @Size(max = 80) String algorithm,
                                      @NotBlank @Size(max = 12000) String publicKeyBase64,
                                      @Size(max = 12000) String signatureBase64) {
    }

    public record KeyBundleResponse(UUID userId, UUID deviceId, List<Map<String, Object>> identityKeys, List<Map<String, Object>> prekeys,
                                    Map<String, Object> transparencyProof) {
    }

    public record KeyTransparencyProofResponse(UUID userId, long latestLogIndex, String latestSignedTreeHeadBase64,
                                               String consistencyProofBase64, String checkpointId, String proofVersion,
                                               List<Map<String, Object>> entries) {
    }

    public record RoomCreateRequest(@NotNull UUID organizationId,
                                    @NotBlank @Size(max = 160) String name,
                                    @NotBlank @Size(max = 80) String classification,
                                    Map<String, Object> policy) {
    }

    public record RoomPolicyUpdateRequest(@NotNull UUID roomId, Map<String, Object> policy, @NotBlank @Size(max = 500) String reason) {
    }

    public record MemberChangeRequest(@NotNull UUID roomId, @NotNull UUID userId, @Size(max = 100) List<UUID> allowedDeviceIds,
                                      @NotBlank @Size(max = 500) String reason) {
    }

    public record AttachmentCreateRequest(@NotNull UUID roomId,
                                          @NotBlank @Size(max = 1024) String objectKey,
                                          @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}|[A-Za-z0-9+/]{43}=|[A-Za-z0-9+/]{42}==|[A-Za-z0-9_-]{43}") String ciphertextSha256,
                                          @Positive long ciphertextBytes,
                                          Map<String, Object> cryptoMetadata) {
    }

    public record EncryptedMessageSubmitRequest(
            @NotNull UUID organizationId,
            UUID roomId,
            @NotNull UUID senderUserId,
            @NotNull UUID senderDeviceId,
            UUID recipientUserId,
            UUID recipientDeviceId,
            @NotBlank @Pattern(regexp = "DIRECT|MLS_GROUP") String messageKind,
            @NotBlank @Size(max = 262144) String ciphertextBase64,
            @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}|[A-Za-z0-9+/]{43}=|[A-Za-z0-9+/]{42}==|[A-Za-z0-9_-]{43}") String ciphertextSha256,
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

    public record DeliveryReceiptRequest(@NotNull UUID messageId, @NotNull UUID deviceId,
                                         @NotBlank @Pattern(regexp = "DELIVERED|READ|FAILED") String receiptType) {
    }

    public record MessageInboxRequest(@NotNull UUID deviceId, Instant after, @Positive @Min(1) Integer limit) {
    }

    public record AuditExportRequest(@NotNull UUID organizationId,
                                     @NotBlank @Pattern(regexp = "DATABASE_BUFFER|INLINE|SIEM") String sinkType,
                                     Instant from,
                                     Instant to) {
    }

    public record EmergencyLockdownRequest(@NotNull UUID organizationId, UUID roomId,
                                           @NotBlank @Size(max = 500) String reason,
                                           @NotBlank @Pattern(regexp = "ORGANIZATION|ROOM") String scope) {
    }
}
