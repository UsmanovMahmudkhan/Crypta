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

