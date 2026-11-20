package com.mft.integration.retry;

import com.mft.integration.exception.FileProcessingException;
import com.mft.integration.exception.IntegrityException;
import com.mft.integration.exception.ValidationException;

import java.io.IOException;

/**
 * Configurable policy defining retry behavior, backoff timing, and retryability rules.
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;
    private final double jitterFactor;

    public RetryPolicy() {
        this(3, 500L, 2.0, 10000L, 0.1);
    }

    public RetryPolicy(int maxAttempts, long initialDelayMs, double backoffMultiplier, long maxDelayMs, double jitterFactor) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialDelayMs = Math.max(0, initialDelayMs);
        this.backoffMultiplier = Math.max(1.0, backoffMultiplier);
        this.maxDelayMs = Math.max(initialDelayMs, maxDelayMs);
        this.jitterFactor = Math.max(0.0, Math.min(1.0, jitterFactor));
    }

    /**
     * Determines whether an error is transient and retryable.
     * Non-retryable: validation failures, checksum integrity mismatches, and layout parse errors.
     * Retryable: transient network, SFTP connection, and socket I/O errors.
     */
    public boolean isRetryable(Throwable t) {
        if (t == null) return false;

        // Domain validation, integrity, or layout exceptions must fail fast without retrying
        if (t instanceof ValidationException || t instanceof IntegrityException || t instanceof FileProcessingException) {
            return false;
        }

        // I/O or network errors are retryable
        if (t instanceof IOException || (t.getCause() != null && t.getCause() instanceof IOException)) {
            return true;
        }

        // Unpack nested causes
        if (t.getCause() != null && t.getCause() != t) {
            return isRetryable(t.getCause());
        }

        // Default to retryable for general unexpected runtime exceptions (e.g. SFTP session drops)
        return true;
    }

    /**
     * Calculates the backoff delay with exponential scaling and jitter.
     */
    public long calculateDelay(int attempt) {
        long delay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attempt - 1));
        if (delay > maxDelayMs) {
            delay = maxDelayMs;
        }
        if (jitterFactor > 0) {
            double jitter = (Math.random() * 2.0 - 1.0) * jitterFactor * delay;
            delay = (long) Math.max(0, delay + jitter);
        }
        return delay;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public double getJitterFactor() {
        return jitterFactor;
    }
}
