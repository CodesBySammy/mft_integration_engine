package com.mft.integration.processing;

/**
 * Record holding field-level validation errors encountered during file parsing.
 */
public record RecordValidationError(
        int lineNumber,
        String field,
        String errorCode,
        String message
) {
    @Override
    public String toString() {
        return String.format("[Line %d] Field [%s]: %s (%s)", lineNumber, field, message, errorCode);
    }
}
