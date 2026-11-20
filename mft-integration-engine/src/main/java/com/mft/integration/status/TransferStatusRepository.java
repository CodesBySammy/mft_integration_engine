package com.mft.integration.status;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing transfer run lifecycle records.
 */
public interface TransferStatusRepository {

    TransferRun save(TransferRun run);

    Optional<TransferRun> findById(String runId);

    List<TransferRun> findByRouteId(String routeId);

    List<TransferRun> findAll();

    void clear();
}
