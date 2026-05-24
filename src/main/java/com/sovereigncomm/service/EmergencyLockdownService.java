package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.EmergencyLockdownRequest;

import java.util.UUID;

public interface EmergencyLockdownService {
    void startLockdown(EmergencyLockdownRequest request);
    void endLockdown(UUID lockdownId, String reason);
}
