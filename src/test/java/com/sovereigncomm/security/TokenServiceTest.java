package com.sovereigncomm.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {
    private final TokenService tokenService = new TokenService("");

    @Test
    void createsOpaquePrefixedTokens() {
        String first = tokenService.newToken();
        String second = tokenService.newToken();

        assertThat(first).startsWith("sc_");
        assertThat(second).startsWith("sc_");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashesAreStableAndNonEmpty() {
        byte[] first = tokenService.sha256("value");
        byte[] second = tokenService.sha256("value");

        assertThat(first).hasSize(32);
        assertThat(first).containsExactly(second);
    }

    @Test
    void sessionTokenHashUsesPepperWhenConfigured() {
        TokenService unpeppered = new TokenService("");
        TokenService peppered = new TokenService("test-pepper-value-with-enough-entropy");

        assertThat(unpeppered.sessionTokenHash("token")).hasSize(32);
        assertThat(peppered.sessionTokenHash("token")).hasSize(32);
        assertThat(peppered.sessionTokenHash("token")).isNotEqualTo(unpeppered.sessionTokenHash("token"));
    }
}
