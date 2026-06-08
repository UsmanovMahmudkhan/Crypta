package com.sovereigncomm.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ProductionSecurityHealthIndicator implements HealthIndicator {
    private final Environment environment;
    private final SecurityVerifierClient verifierClient;
    private final String tokenPepper;
    private final String bootstrapToken;
    private final boolean requireVerifiedDevicesForSessions;
    private final boolean smalltalkGovernanceEnabled;

    public ProductionSecurityHealthIndicator(
            Environment environment,
            SecurityVerifierClient verifierClient,
            @Value("${app.security.token-pepper:}") String tokenPepper,
            @Value("${app.security.bootstrap-token:}") String bootstrapToken,
            @Value("${app.security.require-verified-devices-for-sessions:false}") boolean requireVerifiedDevicesForSessions,
            @Value("${app.governance.smalltalk.enabled:false}") boolean smalltalkGovernanceEnabled) {
        this.environment = environment;
        this.verifierClient = verifierClient;
        this.tokenPepper = tokenPepper == null ? "" : tokenPepper;
        this.bootstrapToken = bootstrapToken == null ? "" : bootstrapToken;
        this.requireVerifiedDevicesForSessions = requireVerifiedDevicesForSessions;
        this.smalltalkGovernanceEnabled = smalltalkGovernanceEnabled;
    }

    @Override
    public Health health() {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return Health.up()
                    .withDetail("mode", "non-prod")
                    .withDetail("verifier", verifierClient.remoteEnabled() ? "remote" : "local-fallback")
                    .build();
        }
        Health.Builder builder = Health.up().withDetail("mode", "prod");
        if (!verifierClient.remoteEnabled()) {
            builder.status(Status.DOWN).withDetail("verifier", "missing app.security.verifier.base-url");
        }
        if (tokenPepper.length() < 32) {
            builder.status(Status.DOWN).withDetail("tokenPepper", "must be at least 32 characters in prod");
        }
        if (bootstrapToken.length() < 32) {
            builder.status(Status.DOWN).withDetail("bootstrapToken", "must be at least 32 characters in prod");
        }
        if (!requireVerifiedDevicesForSessions) {
            builder.status(Status.DOWN).withDetail("deviceSessionTrust", "verified devices must be required in prod");
        }
        if (smalltalkGovernanceEnabled) {
            builder.status(Status.DOWN).withDetail("smalltalkGovernance", "experimental Smalltalk evaluation must stay disabled in prod");
        }
        return builder.build();
    }
}
