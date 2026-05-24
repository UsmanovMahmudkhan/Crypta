package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.WebAuthnFinishRequest;
import com.sovereigncomm.api.dto.CommonDtos.WebAuthnStartResponse;

import java.util.UUID;

public interface AuthService {
    WebAuthnStartResponse startWebAuthnRegistration(UUID userId);
    void finishWebAuthnRegistration(WebAuthnFinishRequest request);
