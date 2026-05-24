package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.AuditExportRequest;
import com.sovereigncomm.api.dto.CommonDtos.EmergencyLockdownRequest;
import com.sovereigncomm.service.AdminGovernanceService;
import com.sovereigncomm.service.AuditService;
import com.sovereigncomm.service.EmergencyLockdownService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminGovernanceService adminGovernanceService;
    private final AuditService auditService;
    private final EmergencyLockdownService emergencyLockdownService;

    public AdminController(AdminGovernanceService adminGovernanceService, AuditService auditService, EmergencyLockdownService emergencyLockdownService) {
        this.adminGovernanceService = adminGovernanceService;
        this.auditService = auditService;
        this.emergencyLockdownService = emergencyLockdownService;
    }

    @PostMapping("/actions")
    void recordAction(@RequestBody String signedAdminActionEnvelope) {
        adminGovernanceService.recordSignedAction(signedAdminActionEnvelope);
    }

    @PostMapping("/audit/export")
    void exportAudit(@Valid @RequestBody AuditExportRequest request) {
        auditService.exportAudit(request);
    }

    @PostMapping("/emergency-lockdowns")
    void startLockdown(@Valid @RequestBody EmergencyLockdownRequest request) {
        emergencyLockdownService.startLockdown(request);
    }
}
