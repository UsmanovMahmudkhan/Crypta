package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.DeviceRegisterRequest;
import com.sovereigncomm.api.dto.CommonDtos.IdResponse;

import java.util.UUID;

public interface DeviceTrustService {
    IdResponse registerDevice(DeviceRegisterRequest request);
    void revokeDevice(UUID deviceId, String reason);
}
