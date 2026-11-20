package com.mft.integration.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mft.integration.workspace.LocalWorkspaceService;
import com.mft.integration.workspace.WorkspaceDirectory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of TransferStatusRepository with persistent disk storage.
 * Ensures transfer run history survives application restarts.
 */
@Repository
public class InMemoryTransferStatusRepository implements TransferStatusRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTransferStatusRepository.class);

    private final Map<String, TransferRun> runs = new ConcurrentHashMap<>();
    private final LocalWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public InMemoryTransferStatusRepository() {
        this(null);
    }

    @Autowired(required = false)
    public InMemoryTransferStatusRepository(LocalWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        if (workspaceService == null) return;
        try {
            Path file = getStorageFile();
            if (Files.exists(file) && Files.size(file) > 0) {
                List<TransferRun> loaded = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
                for (TransferRun run : loaded) {
                    if (run != null && run.getRunId() != null) {
                        runs.put(run.getRunId(), run);
                    }
                }
                log.info("Loaded {} historical transfer run(s) from persistent disk [{}]", runs.size(), file);
            }
        } catch (Exception e) {
            log.warn("Could not load persistent transfer history from disk: {}", e.getMessage());
        }
    }

    @Override
    public synchronized TransferRun save(TransferRun run) {
        if (run == null || run.getRunId() == null) {
            throw new IllegalArgumentException("TransferRun and runId must not be null");
        }
        runs.put(run.getRunId(), run);
        persistToDisk();
        return run;
    }

    @Override
    public Optional<TransferRun> findById(String runId) {
        if (runId == null) return Optional.empty();
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public List<TransferRun> findByRouteId(String routeId) {
        if (routeId == null) return Collections.emptyList();
        return runs.values().stream()
                .filter(r -> routeId.equals(r.getRouteId()))
                .toList();
    }

    @Override
    public List<TransferRun> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(runs.values()));
    }

    @Override
    public synchronized void clear() {
        runs.clear();
        persistToDisk();
    }

    private Path getStorageFile() {
        Path statusDir = workspaceService.getDirectory(WorkspaceDirectory.STATUS);
        return statusDir.resolve("transfers_history.json");
    }

    private void persistToDisk() {
        if (workspaceService == null) return;
        try {
            Path file = getStorageFile();
            if (!Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), new ArrayList<>(runs.values()));
        } catch (IOException e) {
            log.warn("Failed to persist transfer runs to disk: {}", e.getMessage());
        }
    }
}
