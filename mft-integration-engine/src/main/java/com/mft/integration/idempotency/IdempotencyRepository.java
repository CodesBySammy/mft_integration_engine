package com.mft.integration.idempotency;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing idempotency transfer records.
 */
public interface IdempotencyRepository {

    IdempotencyRecord save(IdempotencyRecord record);

    Optional<IdempotencyRecord> findByKey(String key);

    List<IdempotencyRecord> findByRouteId(String routeId);

    List<IdempotencyRecord> findAll();

    void deleteByKey(String key);

    void clear();
}
