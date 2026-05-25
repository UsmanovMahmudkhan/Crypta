package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.BootstrapSessionRequest;
import com.sovereigncomm.api.dto.CommonDtos.WebAuthnFinishRequest;
import com.sovereigncomm.api.dto.CommonDtos.WebAuthnStartResponse;
import com.sovereigncomm.api.dto.CommonDtos.SessionResponse;

import java.util.UUID;

public interface AuthService {
    WebAuthnStartResponse startWebAuthnRegistration(UUID userId);
    SessionResponse issueBootstrapSession(BootstrapSessionRequest request);
    SessionResponse finishWebAuthnRegistration(WebAuthnFinishRequest request);
    WebAuthnStartResponse startWebAuthnLogin(UUID userId);
    SessionResponse finishWebAuthnLogin(WebAuthnFinishRequest request);
}
