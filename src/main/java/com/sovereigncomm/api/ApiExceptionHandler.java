package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.ErrorResponse;
import com.sovereigncomm.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final AuditService auditService;

    public ApiExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorResponse> notFound(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    ResponseEntity<ErrorResponse> forbidden(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflict(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "Request conflicts with existing data or policy", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> internal(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        auditRejectedRequest(status, message, request, requestId);
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                requestId));
    }

    private void auditRejectedRequest(HttpStatus status, String message, HttpServletRequest request, String requestId) {
        if (!request.getRequestURI().startsWith("/api/v1/")) {
            return;
        }
        try {
            auditService.appendSecurityEvent("API_REQUEST_REJECTED", Map.of(
                    "status", status.value(),
                    "path", request.getRequestURI(),
                    "method", request.getMethod(),
                    "requestId", requestId == null ? "" : requestId,
                    "reason", message == null ? status.getReasonPhrase() : message));
        } catch (RuntimeException ignored) {
            // Rejection auditing is best-effort and must never mask the original API error.
        }
    }
}
