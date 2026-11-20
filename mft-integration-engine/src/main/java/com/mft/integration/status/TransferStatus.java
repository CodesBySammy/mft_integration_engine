package com.mft.integration.status;

public enum TransferStatus {
    DISCOVERED,
    DOWNLOADING,
    DOWNLOADED,
    VALIDATING,
    VALIDATED,
    PROCESSING,
    PROCESSED,
    UPLOADING,
    PUBLISHING,
    COMPLETED,
    FAILED,
    RETRY_PENDING,
    SKIPPED_DUPLICATE,
    INTEGRITY_FAILED,
    REJECTED
}
