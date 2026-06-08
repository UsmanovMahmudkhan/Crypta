package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.ErrorResponse;
import com.sovereigncomm.service.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;

class ApiExceptionHandlerTest {
    private final AuditService auditService = Mockito.mock(AuditService.class);
    private final ApiExceptionHandler handler = new ApiExceptionHandler(auditService);

    @Test
    void apiErrorsUseNormalizedEnvelopeAndAuditRejection() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/messages/direct");
        request.setAttribute("requestId", "req-error-1");

        ResponseEntity<ErrorResponse> response = handler.badRequest(new IllegalArgumentException("invalid ciphertext"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody())
                .extracting(ErrorResponse::status, ErrorResponse::error, ErrorResponse::message,
                        ErrorResponse::path, ErrorResponse::requestId)
                .containsExactly(400, "Bad Request", "invalid ciphertext", "/api/v1/messages/direct", "req-error-1");
        Mockito.verify(auditService).appendSecurityEvent(eq("API_REQUEST_REJECTED"), Mockito.<Map<String, Object>>argThat(metadata ->
                metadata.get("status").equals(400)
                        && metadata.get("path").equals("/api/v1/messages/direct")
                        && metadata.get("requestId").equals("req-error-1")
                        && metadata.get("reason").equals("invalid ciphertext")));
    }

    @Test
    void nonApiErrorsSkipRejectionAudit() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        ResponseEntity<ErrorResponse> response = handler.internal(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Unexpected server error");
        Mockito.verifyNoInteractions(auditService);
    }
}
