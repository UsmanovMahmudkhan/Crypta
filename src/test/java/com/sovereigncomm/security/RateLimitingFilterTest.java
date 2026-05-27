package com.sovereigncomm.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {
    @Test
    void returnsNormalizedErrorEnvelopeWhenLimitIsExceeded() throws ServletException, IOException {
        RateLimitingFilter filter = new RateLimitingFilter(true, 1);

        MockHttpServletRequest first = request();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());

        MockHttpServletRequest second = request();
        second.setAttribute("requestId", "req-rate-limit");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getContentAsString())
                .contains("\"status\":429")
                .contains("\"error\":\"Too Many Requests\"")
                .contains("\"path\":\"/api/v1/messages/direct\"")
                .contains("\"requestId\":\"req-rate-limit\"");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/messages/direct");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
