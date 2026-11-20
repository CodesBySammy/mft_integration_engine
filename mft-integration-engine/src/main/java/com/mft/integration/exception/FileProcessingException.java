package com.mft.integration.exception;

public class FileProcessingException extends MftException {

    public FileProcessingException(String message) {
        super("FILE_PROCESSING_ERROR", message);
    }

    public FileProcessingException(String errorCode, String message) {
        super(errorCode, message);
    }

    public FileProcessingException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
