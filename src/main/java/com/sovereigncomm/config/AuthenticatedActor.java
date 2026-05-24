package com.sovereigncomm.config;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedActor(UUID userId, UUID organizationId, UUID deviceId, Set<String> roles, boolean bootstrap) {
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
