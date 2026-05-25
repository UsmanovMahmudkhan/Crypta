package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.AuditExportRequest;
import com.sovereigncomm.api.dto.CommonDtos.EmergencyLockdownRequest;
import com.sovereigncomm.service.AdminGovernanceService;
import com.sovereigncomm.service.AuditService;
import com.sovereigncomm.service.EmergencyLockdownService;
import com.sovereigncomm.service.MDMIntegrationService;
import com.sovereigncomm.service.SIEMExportService;
import com.sovereigncomm.security.SecurityVerifierClient;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminGovernanceService adminGovernanceService;
    private final AuditService auditService;
    private final EmergencyLockdownService emergencyLockdownService;
    private final MDMIntegrationService mdmIntegrationService;
    private final SIEMExportService siemExportService;
    private final SecurityVerifierClient securityVerifierClient;
    private final JdbcTemplate jdbcTemplate;

    public AdminController(AdminGovernanceService adminGovernanceService, AuditService auditService, EmergencyLockdownService emergencyLockdownService,
                           MDMIntegrationService mdmIntegrationService, SIEMExportService siemExportService,
                           SecurityVerifierClient securityVerifierClient, JdbcTemplate jdbcTemplate) {
        this.adminGovernanceService = adminGovernanceService;
        this.auditService = auditService;
        this.emergencyLockdownService = emergencyLockdownService;
        this.mdmIntegrationService = mdmIntegrationService;
        this.siemExportService = siemExportService;
        this.securityVerifierClient = securityVerifierClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/actions")
    void recordAction(@RequestBody String signedAdminActionEnvelope) {
        adminGovernanceService.recordSignedAction(signedAdminActionEnvelope);
    }

    @PostMapping("/audit/export")
    void exportAudit(@Valid @RequestBody AuditExportRequest request) {
        auditService.exportAudit(request);
    }

    @PostMapping("/siem/events")
    void exportEvent(@RequestBody String normalizedAuditEventJson) {
        siemExportService.exportEvent(normalizedAuditEventJson);
    }

    @PostMapping("/mdm/sync/{organizationId}")
    void syncMdm(@PathVariable UUID organizationId) {
        mdmIntegrationService.syncDevicePosture(organizationId);
    }

    @PostMapping("/emergency-lockdowns")
    void startLockdown(@Valid @RequestBody EmergencyLockdownRequest request) {
        emergencyLockdownService.startLockdown(request);
    }

    @PostMapping("/emergency-lockdowns/{lockdownId}/end")
    void endLockdown(@PathVariable UUID lockdownId, @RequestBody(required = false) Map<String, String> body) {
        emergencyLockdownService.endLockdown(lockdownId, body == null ? "api_request" : body.getOrDefault("reason", "api_request"));
    }

    @GetMapping("/security/verifier")
    SecurityVerifierClient.VerifierHealth verifierHealth() {
        return securityVerifierClient.health();
    }

    @GetMapping("/security/transparency-monitor/{organizationId}")
    Map<String, Object> transparencyMonitor(@PathVariable UUID organizationId) {
        return jdbcTemplate.query("""
                        SELECT count(*) AS entry_count, max(log_index) AS latest_index, max(created_at) AS latest_entry_at
                        FROM key_transparency_entries
                        WHERE organization_id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return Map.of("entryCount", 0, "latestLogIndex", -1);
                    }
                    return Map.of(
                            "entryCount", rs.getLong("entry_count"),
                            "latestLogIndex", rs.getObject("latest_index") == null ? -1 : rs.getLong("latest_index"),
                            "latestEntryAt", rs.getTimestamp("latest_entry_at") == null ? "" : rs.getTimestamp("latest_entry_at").toInstant().toString(),
                            "verifierMode", securityVerifierClient.remoteEnabled() ? "remote" : "local-fallback");
                },
                organizationId);
    }

    @PostMapping("/security/audit/verify/{organizationId}")
    SecurityVerifierClient.AuditVerificationResponse verifyAuditChain(@PathVariable UUID organizationId) {
        return jdbcTemplate.query("""
                        SELECT count(*) AS event_count, max(event_hash) AS latest_event_hash
                        FROM audit_events
                        WHERE organization_id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return new SecurityVerifierClient.AuditVerificationResponse(true, 0, "empty");
                    }
                    byte[] latest = rs.getBytes("latest_event_hash");
                    return securityVerifierClient.verifyAuditChain(new SecurityVerifierClient.AuditVerificationRequest(
                            organizationId,
                            rs.getInt("event_count"),
                            latest == null ? "" : Base64.getEncoder().encodeToString(latest)));
                },
                organizationId);
    }
}
