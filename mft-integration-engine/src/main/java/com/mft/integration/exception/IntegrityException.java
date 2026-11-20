package com.mft.integration.exception;

public class IntegrityException extends MftException {

    public IntegrityException(String message) {
        super("INTEGRITY_FAILED", message);
    }

    public IntegrityException(String expected, String actual, String filePath) {
        super("INTEGRITY_FAILED", String.format(
                "Checksum mismatch for file [%s]. Expected: [%s], Calculated: [%s]",
                filePath, expected, actual));
    }
}
