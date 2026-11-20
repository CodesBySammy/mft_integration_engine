package com.mft.integration.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service managing transfer idempotency checks and lifecycle updates.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository repository;

    public IdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
    }

    /**
     * Checks if this transfer has already successfully completed.
     */
    public boolean isAlreadyProcessed(IdempotencyKey key) {
        if (key == null) return false;
        Optional<IdempotencyRecord> existing = repository.findByKey(key.getHash());
        return existing.isPresent() && existing.get().getStatus() == IdempotencyStatus.COMPLETED;
    }

    /**
     * Retrieves an existing record for this idempotency key.
     */
    public Optional<IdempotencyRecord> getRecord(IdempotencyKey key) {
        if (key == null) return Optional.empty();
        return repository.findByKey(key.getHash());
    }

    /**
     * Registers a new or reprocessed transfer in IN_PROGRESS status.
     */
    public IdempotencyRecord registerTransfer(IdempotencyKey key, String runId) {
        IdempotencyRecord record = new IdempotencyRecord(
                key.getHash(),
                key.getRouteId(),
                key.getSourceConnectionId(),
                key.getSourceFilePath(),
                key.getFileSize(),
                key.getSourceChecksum(),
                runId,
                IdempotencyStatus.IN_PROGRESS
        );
        log.debug("Registered transfer idempotency key: hash=[{}], src=[{}]", key.getHash(), key.getSourceFilePath());
        return repository.save(record);
    }

    /**
     * Marks the transfer as successfully completed.
     */
    public void markCompleted(IdempotencyKey key, String destinationPath) {
        if (key == null) return;
        repository.findByKey(key.getHash()).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setDestinationPath(destinationPath);
            record.setUpdatedAt(Instant.now());
            repository.save(record);
            log.info("Transfer idempotency marked COMPLETED for key [{}] -> dst=[{}]", key.getHash(), destinationPath);
        });
    }

    /**
     * Marks the transfer as failed with an error reason.
     */
    public void markFailed(IdempotencyKey key, String errorReason) {
        if (key == null) return;
        repository.findByKey(key.getHash()).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            record.setErrorReason(errorReason);
            record.setUpdatedAt(Instant.now());
            repository.save(record);
            log.warn("Transfer idempotency marked FAILED for key [{}]: {}", key.getHash(), errorReason);
        });
    }

    public void clearRecords() {
        repository.clear();
        log.info("Cleared all idempotency duplicate records cache.");
    }

    public List<IdempotencyRecord> getAllRecords() {
        return repository.findAll();
    }

    public List<IdempotencyRecord> getRecordsForRoute(String routeId) {
        return repository.findByRouteId(routeId);
    }
}
