package com.sovereigncomm.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesProvidedRequestIdAcrossRequestResponseAndMdc() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/messages/inbox");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> chainRequestId = new AtomicReference<>();
        request.addHeader("X-Request-Id", "req-client-1");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainRequestId.set(MDC.get("requestId")));

        assertThat(request.getAttribute("requestId")).isEqualTo("req-client-1");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-client-1");
        assertThat(chainRequestId.get()).isEqualTo("req-client-1");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void createsRequestIdWhenHeaderIsBlank() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/messages/direct");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Request-Id", " ");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String requestId = (String) request.getAttribute("requestId");
        assertThat(UUID.fromString(requestId)).isNotNull();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(requestId);
    }
}
