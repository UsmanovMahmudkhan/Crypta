package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class NoopScaffoldServices implements AuthService, OrganizationService, UserService, DeviceTrustService, KeyService,
        PreKeyService, KeyTransparencyService, DirectMessageService, GroupMessageService, MLSGroupService,
        RoomPolicyService, DeliveryService, NotificationService, AttachmentService, AuditService,
        AdminGovernanceService, MDMIntegrationService, SIEMExportService, EmergencyLockdownService {

    @Override
    public WebAuthnStartResponse startWebAuthnRegistration(UUID userId) {
        return new WebAuthnStartResponse("scaffold-challenge", Map.of("userId", userId));
    }

    @Override
    public void finishWebAuthnRegistration(WebAuthnFinishRequest request) {
    }

    @Override
    public WebAuthnStartResponse startWebAuthnLogin(UUID userId) {
        return new WebAuthnStartResponse("scaffold-challenge", Map.of("userId", userId));
    }

    @Override
    public void finishWebAuthnLogin(WebAuthnFinishRequest request) {
    }

    @Override
    public IdResponse createOrganization(OrganizationCreateRequest request) {
        return created();
    }

    @Override
    public IdResponse registerUser(UserRegisterRequest request) {
        return created();
    }

    @Override
    public IdResponse registerDevice(DeviceRegisterRequest request) {
        return created();
    }

    @Override
    public void revokeDevice(UUID deviceId, String reason) {
    }

    @Override
    public void uploadIdentityKey(PublicKeyUploadRequest request) {
    }

    @Override
    public KeyBundleResponse fetchKeyBundle(UUID userId) {
        return new KeyBundleResponse(userId, UUID.randomUUID(), List.of(), List.of());
    }

    @Override
    public void uploadSignedPreKey(PreKeyUploadRequest request) {
    }

    @Override
    public void uploadOneTimePreKey(PreKeyUploadRequest request) {
    }

    @Override
    public void uploadPqPreKey(PreKeyUploadRequest request) {
    }

    @Override
    public void appendKeyEvent(UUID deviceId, String canonicalEntryJson, String entrySignatureBase64) {
    }

    @Override
    public Map<String, Object> proof(UUID userId) {
        return Map.of("userId", userId, "proof", "scaffold");
    }

    @Override
    public void acceptCiphertextEnvelope(String encryptedDirectMessageEnvelope) {
    }

    @Override
    public IdResponse createMissionRoom(RoomCreateRequest request) {
        return created();
    }

    @Override
    public void inviteMember(MemberChangeRequest request) {
    }

    @Override
    public void removeMember(MemberChangeRequest request) {
    }

    @Override
    public void updatePolicy(RoomPolicyUpdateRequest request) {
    }

    @Override
    public void enqueueForRecipient(UUID recipientDeviceId, String encryptedEnvelope) {
    }

    @Override
    public void sendGenericNotification(UUID deviceId, String urgency) {
    }

    @Override
    public IdResponse createEncryptedAttachment(AttachmentCreateRequest request) {
        return created();
    }

    @Override
    public Map<String, Object> createEncryptedDownload(UUID attachmentId) {
        return Map.of("attachmentId", attachmentId, "url", "https://object-storage.example.invalid/presigned");
    }

    @Override
    public void appendSecurityEvent(String eventType, Map<String, Object> metadata) {
    }

    @Override
    public void exportAudit(AuditExportRequest request) {
    }

    @Override
    public void recordSignedAction(String signedAdminActionEnvelope) {
    }

    @Override
    public void syncDevicePosture(UUID organizationId) {
    }

    @Override
    public void exportEvent(String normalizedAuditEventJson) {
    }

    @Override
    public void startLockdown(EmergencyLockdownRequest request) {
    }

    @Override
    public void endLockdown(UUID lockdownId, String reason) {
    }

