package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.BootstrapSessionRequest;
import com.sovereigncomm.api.dto.CommonDtos.SessionResponse;
import com.sovereigncomm.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bootstrap")
public class BootstrapController {
    private final AuthService authService;

    public BootstrapController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sessions")
    SessionResponse issueSession(@Valid @RequestBody BootstrapSessionRequest request) {
        return authService.issueBootstrapSession(request);
    }
}
