package com.mft.integration.exception;

public class ValidationException extends MftException {

    public ValidationException(String message) {
        super("CONFIG_VALIDATION_ERROR", message);
    }

    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
