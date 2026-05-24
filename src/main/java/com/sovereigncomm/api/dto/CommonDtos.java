package com.sovereigncomm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommonDtos {
    private CommonDtos() {
    }

    public record IdResponse(UUID id, Instant createdAt) {
    }

    public record OrganizationCreateRequest(@NotBlank String name, @NotBlank String jurisdiction, String externalTenantId) {
    }

    public record UserRegisterRequest(@NotNull UUID organizationId, @NotBlank String email, @NotBlank String displayName, List<String> roles) {
    }

    public record DeviceRegisterRequest(
            @NotNull UUID userId,
            @NotBlank String platform,
            @NotBlank String deviceName,
            @NotBlank String attestationFormat,
            @NotBlank String attestationObjectBase64,
            @NotBlank String deviceSigningPublicKeyBase64) {
