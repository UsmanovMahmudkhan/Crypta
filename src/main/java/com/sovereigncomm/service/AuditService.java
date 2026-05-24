package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.AuditExportRequest;

import java.util.Map;

public interface AuditService {
    void appendSecurityEvent(String eventType, Map<String, Object> metadata);
    void exportAudit(AuditExportRequest request);
}
