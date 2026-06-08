package com.sovereigncomm.security;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSecurityHealthIndicatorTest {
    @Test
    void reportsNonProdAsUpWithVerifierMode() {
        SecurityVerifierClient verifierClient = Mockito.mock(SecurityVerifierClient.class);
        Mockito.when(verifierClient.remoteEnabled()).thenReturn(false);

        Health health = indicator(new MockEnvironment(), verifierClient, "", "", false, false).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("mode", "non-prod");
        assertThat(health.getDetails()).containsEntry("verifier", "local-fallback");
    }

    @Test
    void rejectsUnsafeProductionSettings() {
        SecurityVerifierClient verifierClient = Mockito.mock(SecurityVerifierClient.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        Health health = indicator(environment, verifierClient, "short", "short", false, true).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("verifier", "missing app.security.verifier.base-url")
                .containsEntry("tokenPepper", "must be at least 32 characters in prod")
                .containsEntry("bootstrapToken", "must be at least 32 characters in prod")
                .containsEntry("deviceSessionTrust", "verified devices must be required in prod")
                .containsEntry("smalltalkGovernance", "experimental Smalltalk evaluation must stay disabled in prod");
    }

    @Test
    void acceptsHardenedProductionSettings() {
        SecurityVerifierClient verifierClient = Mockito.mock(SecurityVerifierClient.class);
        Mockito.when(verifierClient.remoteEnabled()).thenReturn(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        Health health = indicator(environment, verifierClient, "x".repeat(32), "y".repeat(32), true, false).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("mode", "prod");
    }

    private ProductionSecurityHealthIndicator indicator(MockEnvironment environment, SecurityVerifierClient verifierClient,
                                                        String tokenPepper, String bootstrapToken,
                                                        boolean requireVerifiedDevicesForSessions,
                                                        boolean smalltalkGovernanceEnabled) {
        return new ProductionSecurityHealthIndicator(environment, verifierClient, tokenPepper, bootstrapToken,
                requireVerifiedDevicesForSessions, smalltalkGovernanceEnabled);
    }
}
