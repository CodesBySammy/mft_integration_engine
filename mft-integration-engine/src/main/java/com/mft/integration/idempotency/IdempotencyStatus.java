package com.mft.integration.idempotency;

/**
 * Status of an idempotency transfer entry.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
