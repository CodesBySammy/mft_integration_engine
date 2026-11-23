package com.mft.integration.api;

import com.mft.integration.api.dto.ErrorResponse;
import com.mft.integration.exception.ConcurrencyLimitExceededException;
import com.mft.integration.exception.FileProcessingException;
import com.mft.integration.exception.IntegrityException;
import com.mft.integration.exception.MftException;
import com.mft.integration.exception.RouteExecutionLockedException;
import com.mft.integration.exception.SftpConnectionException;
import com.mft.integration.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global REST exception handler mapping engine exceptions to standardized JSON ErrorResponse objects.
 * Prevents raw stack trace leakage and credential exposure.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex, HttpServletRequest request) {
        log.warn("Validation error on [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        log.warn("Method argument validation failed on [{}]: {}", request.getRequestURI(), details);
        ErrorResponse error = new ErrorResponse("INVALID_ARGUMENT", details, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(RouteExecutionLockedException.class)
    public ResponseEntity<ErrorResponse> handleRouteExecutionLocked(RouteExecutionLockedException ex, HttpServletRequest request) {
        log.warn("Route execution locked on [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ConcurrencyLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleConcurrencyLimitExceeded(ConcurrencyLimitExceededException ex, HttpServletRequest request) {
        log.warn("Engine concurrency limit exceeded on [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    @ExceptionHandler(IntegrityException.class)
    public ResponseEntity<ErrorResponse> handleIntegrityException(IntegrityException ex, HttpServletRequest request) {
        log.error("Integrity check failure on [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ErrorResponse> handleFileProcessingException(FileProcessingException ex, HttpServletRequest request) {
        log.error("File processing error on [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(SftpConnectionException.class)
    public ResponseEntity<ErrorResponse> handleSftpConnectionException(SftpConnectionException ex, HttpServletRequest request) {
        log.error("SFTP connectivity failure on [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getErrorCode(), "SFTP connection error: " + ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(MftException.class)
    public ResponseEntity<ErrorResponse> handleMftException(MftException ex, HttpServletRequest request) {
        log.error("MFT operational exception on [{}]: [{}] {}", request.getRequestURI(), ex.getErrorCode(), ex.getMessage());
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if ("ROUTE_NOT_FOUND".equals(ex.getErrorCode()) || "CONNECTION_NOT_FOUND".equals(ex.getErrorCode())) {
            status = HttpStatus.NOT_FOUND;
        } else if ("VALIDATION_FAILED".equals(ex.getErrorCode())) {
            status = HttpStatus.BAD_REQUEST;
        }

        ErrorResponse error = new ErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server exception on [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred processing your request.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
