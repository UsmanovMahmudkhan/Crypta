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

