package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.DeviceRegisterRequest;
import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.service.DeviceTrustService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceTrustService deviceTrustService;

    public DeviceController(DeviceTrustService deviceTrustService) {
        this.deviceTrustService = deviceTrustService;
    }

    @PostMapping
    IdResponse register(@Valid @RequestBody DeviceRegisterRequest request) {
        return deviceTrustService.registerDevice(request);
    }
}
