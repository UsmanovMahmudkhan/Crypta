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
