package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.AuditExportRequest;
import com.sovereigncomm.api.dto.CommonDtos.EmergencyLockdownRequest;
import com.sovereigncomm.service.AdminGovernanceService;
import com.sovereigncomm.service.AuditService;
import com.sovereigncomm.service.EmergencyLockdownService;
import com.sovereigncomm.service.MDMIntegrationService;
import com.sovereigncomm.service.SIEMExportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminGovernanceService adminGovernanceService;
    private final AuditService auditService;
    private final EmergencyLockdownService emergencyLockdownService;
    private final MDMIntegrationService mdmIntegrationService;
    private final SIEMExportService siemExportService;

    public AdminController(AdminGovernanceService adminGovernanceService, AuditService auditService, EmergencyLockdownService emergencyLockdownService,
                           MDMIntegrationService mdmIntegrationService, SIEMExportService siemExportService) {
        this.adminGovernanceService = adminGovernanceService;
        this.auditService = auditService;
        this.emergencyLockdownService = emergencyLockdownService;
        this.mdmIntegrationService = mdmIntegrationService;
        this.siemExportService = siemExportService;
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
}
