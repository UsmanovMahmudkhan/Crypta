package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.WebAuthnFinishRequest;
import com.sovereigncomm.api.dto.CommonDtos.WebAuthnStartResponse;
import com.sovereigncomm.api.dto.CommonDtos.SessionResponse;
import com.sovereigncomm.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webauthn")
public class WebAuthnController {
    private final AuthService authService;

    public WebAuthnController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registration/options/{userId}")
    WebAuthnStartResponse registrationOptions(@PathVariable UUID userId) {
        return authService.startWebAuthnRegistration(userId);
    }

    @PostMapping("/registration/finish")
    SessionResponse finishRegistration(@Valid @RequestBody WebAuthnFinishRequest request) {
        return authService.finishWebAuthnRegistration(request);
    }

    @PostMapping("/login/options/{userId}")
    WebAuthnStartResponse loginOptions(@PathVariable UUID userId) {
        return authService.startWebAuthnLogin(userId);
    }

    @PostMapping("/login/finish")
    SessionResponse finishLogin(@Valid @RequestBody WebAuthnFinishRequest request) {
        return authService.finishWebAuthnLogin(request);
    }
}
