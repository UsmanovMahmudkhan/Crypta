package com.sovereigncomm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final int requestsPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.requests-per-minute:600}") int requestsPerMinute) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || !isSensitiveRoute(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = request.getRemoteAddr() + ":" + routeBucket(request.getRequestURI());
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(key, (ignored, current) ->
                current == null || current.minute != minute ? new Window(minute) : current);
        if (window.count.incrementAndGet() > requestsPerMinute) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSensitiveRoute(String uri) {
        return uri.startsWith("/api/v1/webauthn/")
                || uri.startsWith("/api/v1/bootstrap/")
                || uri.startsWith("/api/v1/keys/")
                || uri.startsWith("/api/v1/messages/")
                || uri.startsWith("/api/v1/admin/")
                || uri.startsWith("/api/v1/governance/");
    }

    private String routeBucket(String uri) {
        if (uri.startsWith("/api/v1/webauthn/") || uri.startsWith("/api/v1/bootstrap/")) {
            return "auth";
        }
        if (uri.startsWith("/api/v1/keys/")) {
            return "keys";
        }
        if (uri.startsWith("/api/v1/messages/")) {
            return "messages";
        }
        if (uri.startsWith("/api/v1/admin/")) {
            return "admin";
        }
        return "governance";
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
