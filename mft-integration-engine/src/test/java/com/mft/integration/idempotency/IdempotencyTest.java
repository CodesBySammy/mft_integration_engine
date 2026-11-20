package com.mft.integration.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyTest {

    private InMemoryIdempotencyRepository repository;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryIdempotencyRepository();
        service = new IdempotencyService(repository);
    }

    @Test
    @DisplayName("Generates deterministic SHA-256 hash for identical keys")
    void testIdempotencyKeyDeterministicHash() {
        IdempotencyKey key1 = new IdempotencyKey("R_RAW_COPY", "C_FILE_IN", "/upload/file1.txt", 1024L, "abc123hash");
        IdempotencyKey key2 = new IdempotencyKey("R_RAW_COPY", "C_FILE_IN", "/upload/file1.txt", 1024L, "abc123hash");
        IdempotencyKey keyDifferent = new IdempotencyKey("R_RAW_COPY", "C_FILE_IN", "/upload/file1.txt", 1025L, "abc123hash");

        assertThat(key1.getHash()).isEqualTo(key2.getHash());
        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
        assertThat(key1.getHash()).isNotEqualTo(keyDifferent.getHash());
    }

    @Test
    @DisplayName("Tracks lifecycle from registration to completion")
    void testIdempotencyLifecycle() {
        IdempotencyKey key = new IdempotencyKey("R_RAW_COPY", "C_FILE_IN", "/upload/trade.dat", 500L, "hash500");

        assertThat(service.isAlreadyProcessed(key)).isFalse();

        // 1. Register transfer
        IdempotencyRecord record = service.registerTransfer(key, "RUN_001");
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(service.isAlreadyProcessed(key)).isFalse(); // In-progress is not yet completed

        // 2. Mark completed
        service.markCompleted(key, "/incoming/trade.dat");
        assertThat(service.isAlreadyProcessed(key)).isTrue();

        Optional<IdempotencyRecord> completed = service.getRecord(key);
        assertThat(completed).isPresent();
        assertThat(completed.get().getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(completed.get().getDestinationPath()).isEqualTo("/incoming/trade.dat");

        // 3. Mark failed updates record
        service.markFailed(key, "Connection timeout");
        assertThat(service.isAlreadyProcessed(key)).isFalse();
        assertThat(completed.get().getStatus()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(completed.get().getErrorReason()).isEqualTo("Connection timeout");
    }
}
