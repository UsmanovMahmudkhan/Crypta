package com.sovereigncomm.security;

import com.sovereigncomm.config.AuthenticatedActor;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ApiAuthenticationFilterTest {
    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final ApiAuthenticationFilter filter = new ApiAuthenticationFilter(
            jdbcTemplate,
            new TokenService("unit-test-pepper"),
            "bootstrap-secret",
            true,
            15);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bootstrapTokenAuthenticatesAsDedicatedBootstrapRole() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/organizations");
        request.addHeader("X-Bootstrap-Token", "bootstrap-secret");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        AuthenticatedActor actor = (AuthenticatedActor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(actor.bootstrap()).isTrue();
        assertThat(actor.roles()).containsExactly("BOOTSTRAP");
        Mockito.verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void bearerSessionLookupIncludesRevocationExpiryIdleAndVerifiedDevicePredicates() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/messages/inbox");
        request.addHeader("Authorization", "Bearer sc_test");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).query(
                sql.capture(),
                any(ResultSetExtractor.class),
                any(byte[].class),
                eq(15),
                eq(true));
        assertThat(sql.getValue())
                .contains("s.revoked_at IS NULL")
                .contains("s.expires_at > now()")
                .contains("s.last_seen_at IS NULL OR s.last_seen_at > now() - (? * interval '1 minute')")
                .contains("d.trust_state = 'VERIFIED' AND d.revoked_at IS NULL");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
