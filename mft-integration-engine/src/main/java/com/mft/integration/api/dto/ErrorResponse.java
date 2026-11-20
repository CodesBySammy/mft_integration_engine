package com.mft.integration.api.dto;

import java.time.Instant;

/**
 * Standard error response DTO for handled and unhandled API exceptions.
 */
public class ErrorResponse {

    private final boolean success;
    private final String errorCode;
    private final String errorMessage;
    private final String path;
    private final Instant timestamp;

    public ErrorResponse(String errorCode, String errorMessage, String path) {
        this.success = false;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.path = path;
        this.timestamp = Instant.now();
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPath() {
        return path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
