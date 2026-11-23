package com.mft.integration.exception;

/**
 * Thrown when the maximum number of concurrent active file transfers across the engine is reached.
 * Implements the bulkhead pattern to protect system stability.
 */
public class ConcurrencyLimitExceededException extends MftException {

    public ConcurrencyLimitExceededException(String message) {
        super("CONCURRENCY_LIMIT_EXCEEDED", message);
    }
}
