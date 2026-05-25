package com.sovereigncomm.security;

import com.sovereigncomm.config.AuthenticatedActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class ApiAuthenticationFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final String bootstrapToken;
    private final boolean requireVerifiedDevicesForSessions;
    private final int sessionIdleTimeoutMinutes;

    public ApiAuthenticationFilter(
            JdbcTemplate jdbcTemplate,
            TokenService tokenService,
            @Value("${app.security.bootstrap-token:}") String bootstrapToken,
            @Value("${app.security.require-verified-devices-for-sessions:false}") boolean requireVerifiedDevicesForSessions,
            @Value("${app.security.session-idle-timeout-minutes:60}") int sessionIdleTimeoutMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
        this.bootstrapToken = bootstrapToken == null ? "" : bootstrapToken;
        this.requireVerifiedDevicesForSessions = requireVerifiedDevicesForSessions;
        this.sessionIdleTimeoutMinutes = sessionIdleTimeoutMinutes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        authenticateBootstrap(request);
        authenticateBearer(request);
        filterChain.doFilter(request, response);
    }

    private void authenticateBootstrap(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null || bootstrapToken.isBlank()) {
            return;
        }
        String supplied = request.getHeader("X-Bootstrap-Token");
        if (supplied == null || !MessageDigest.isEqual(
                bootstrapToken.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            return;
        }
        AuthenticatedActor actor = new AuthenticatedActor(null, null, null, Set.of("PLATFORM_OPERATOR"), true);
        SecurityContextHolder.getContext().setAuthentication(authentication(actor));
    }

    private void authenticateBearer(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return;
        }
        byte[] tokenHash = tokenService.sessionTokenHash(authorization.substring("Bearer ".length()).trim());
        jdbcTemplate.query("""
                        SELECT s.user_id, s.device_id, u.organization_id,
                               COALESCE(string_agg(r.role, ','), '') AS roles
                        FROM api_sessions s
                        JOIN users u ON u.id = s.user_id
                        LEFT JOIN devices d ON d.id = s.device_id
                        LEFT JOIN user_roles r ON r.user_id = u.id
                        WHERE s.token_hash = ?
                          AND s.revoked_at IS NULL
                          AND s.expires_at > now()
                          AND (s.last_seen_at IS NULL OR s.last_seen_at > now() - (? * interval '1 minute'))
                          AND (? = false OR (d.trust_state = 'VERIFIED' AND d.revoked_at IS NULL))
                        GROUP BY s.user_id, s.device_id, u.organization_id
                        """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    UUID userId = rs.getObject("user_id", UUID.class);
                    UUID deviceId = rs.getObject("device_id", UUID.class);
                    UUID organizationId = rs.getObject("organization_id", UUID.class);
                    Set<String> roles = new HashSet<>();
                    String roleCsv = rs.getString("roles");
                    if (roleCsv != null && !roleCsv.isBlank()) {
                        roles.addAll(Arrays.asList(roleCsv.split(",")));
                    }
                    AuthenticatedActor actor = new AuthenticatedActor(userId, organizationId, deviceId, roles, false);
                    SecurityContextHolder.getContext().setAuthentication(authentication(actor));
                    jdbcTemplate.update("UPDATE api_sessions SET last_seen_at = now() WHERE token_hash = ?", tokenHash);
                    return null;
                },
                tokenHash, sessionIdleTimeoutMinutes, requireVerifiedDevicesForSessions);
    }

    private UsernamePasswordAuthenticationToken authentication(AuthenticatedActor actor) {
        return new UsernamePasswordAuthenticationToken(
                actor,
                "N/A",
                actor.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
    }
}
