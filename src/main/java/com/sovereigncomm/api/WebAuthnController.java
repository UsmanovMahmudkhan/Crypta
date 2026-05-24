package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.WebAuthnFinishRequest;
import com.sovereigncomm.api.dto.CommonDtos.WebAuthnStartResponse;
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
