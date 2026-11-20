package com.mft.integration.status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for tracking the live status and lifecycle history of transfer runs.
 * Delegates storage to TransferStatusRepository.
 */
@Service
public class TransferStatusService {

    private static final Logger log = LoggerFactory.getLogger(TransferStatusService.class);

    private final TransferStatusRepository repository;

    public TransferStatusService(TransferStatusRepository repository) {
        this.repository = repository;
    }

    public void registerRun(TransferRun run) {
        repository.save(run);
        log.debug("Registered transfer run [{}] with status [{}]", run.getRunId(), run.getStatus());
    }

    public void updateStatus(String runId, TransferStatus status) {
        repository.findById(runId).ifPresent(run -> {
            run.setStatus(status);
            repository.save(run);
            log.info("Status transition: runId=[{}] -> status=[{}]", runId, status);
        });
    }

    public Optional<TransferRun> getRun(String runId) {
        return repository.findById(runId);
    }

    public List<TransferRun> getRunsForRoute(String routeId) {
        return repository.findByRouteId(routeId);
    }

    public List<TransferRun> getAllRuns() {
        return repository.findAll();
    }
}
