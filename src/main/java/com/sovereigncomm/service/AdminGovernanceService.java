package com.sovereigncomm.service;

import com.sovereigncomm.security.SecurityVerifierClient;

import java.util.Map;
import java.util.UUID;

public interface AdminGovernanceService {
    void recordSignedAction(String signedAdminActionEnvelope);
    Map<String, Object> transparencyMonitor(UUID organizationId);
    SecurityVerifierClient.AuditVerificationResponse verifyAuditChain(UUID organizationId);
}
