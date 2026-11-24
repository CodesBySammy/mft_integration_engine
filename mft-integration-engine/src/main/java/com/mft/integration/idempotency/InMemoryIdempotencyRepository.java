package com.mft.integration.idempotency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mft.integration.status.TransferRun;
import com.mft.integration.status.TransferStatus;
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
 * Thread-safe implementation of IdempotencyRepository with persistent disk storage.
 * Ensures that transferred file records survive engine restarts and are never re-sent.
 */
@Repository
public class InMemoryIdempotencyRepository implements IdempotencyRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryIdempotencyRepository.class);

    private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();
    private final LocalWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private com.mft.integration.persistence.DatabasePersistenceService databasePersistenceService;

    public InMemoryIdempotencyRepository() {
        this(null);
    }

    @Autowired(required = false)
    public InMemoryIdempotencyRepository(LocalWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        if (workspaceService == null) return;
        try {
            Path file = getStorageFile();
            if (Files.exists(file) && Files.size(file) > 0) {
                List<IdempotencyRecord> loaded = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
                for (IdempotencyRecord record : loaded) {
                    if (record != null && record.getKey() != null) {
                        records.put(record.getKey(), record);
                    }
                }
                log.info("Loaded {} idempotency record(s) from persistent disk [{}]", records.size(), file);
            }

            // Also seed from transfers_history.json if present
            seedFromTransferHistory();
        } catch (Exception e) {
            log.warn("Could not load persistent idempotency records: {}", e.getMessage());
        }
    }

    private void seedFromTransferHistory() {
        try {
            Path statusDir = workspaceService.getDirectory(WorkspaceDirectory.STATUS);
            Path historyFile = statusDir.resolve("transfers_history.json");
            if (Files.exists(historyFile) && Files.size(historyFile) > 0) {
                List<TransferRun> runs = objectMapper.readValue(historyFile.toFile(), new TypeReference<>() {});
                int seeded = 0;
                for (TransferRun run : runs) {
                    if (run != null && run.getStatus() == TransferStatus.COMPLETED && run.getSourceChecksum() != null) {
                        String remotePath = "/upload/" + run.getSourceFilename();
                        IdempotencyKey key = new IdempotencyKey(
                                run.getRouteId(),
                                run.getSourceConnection() != null ? run.getSourceConnection() : "C_FILE_IN",
                                remotePath,
                                run.getBytesTransferred(),
                                run.getSourceChecksum()
                        );
                        if (!records.containsKey(key.getHash())) {
                            IdempotencyRecord rec = new IdempotencyRecord(
                                    key.getHash(),
                                    run.getRouteId(),
                                    run.getSourceConnection(),
                                    remotePath,
                                    run.getBytesTransferred(),
                                    run.getSourceChecksum(),
                                    run.getRunId(),
                                    IdempotencyStatus.COMPLETED
                            );
                            rec.setDestinationPath(run.getDestinationFilename());
                            records.put(key.getHash(), rec);
                            seeded++;
                        }
                    }
                }
                if (seeded > 0) {
                    log.info("Seeded {} completed file records into idempotency registry from transfer history", seeded);
                    persistToDisk();
                }
            }
        } catch (Exception e) {
            log.debug("Seeding from transfer history skipped: {}", e.getMessage());
        }
    }

    @Override
    public synchronized IdempotencyRecord save(IdempotencyRecord record) {
        if (record == null || record.getKey() == null) {
            throw new IllegalArgumentException("Record and record key must not be null");
        }
        records.put(record.getKey(), record);
        persistToDisk();
        if (databasePersistenceService != null) {
            databasePersistenceService.saveIdempotencyRecord(record);
        }
        return record;
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(records.get(key));
    }

    @Override
    public List<IdempotencyRecord> findByRouteId(String routeId) {
        if (routeId == null) return Collections.emptyList();
        return records.values().stream()
                .filter(r -> routeId.equals(r.getRouteId()))
                .toList();
    }

    @Override
    public List<IdempotencyRecord> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(records.values()));
    }

    @Override
    public synchronized void deleteByKey(String key) {
        if (key != null) {
            records.remove(key);
            persistToDisk();
        }
    }

    @Override
    public synchronized void clear() {
        records.clear();
        persistToDisk();
    }

    private Path getStorageFile() {
        Path statusDir = workspaceService.getDirectory(WorkspaceDirectory.STATUS);
        return statusDir.resolve("idempotency_records.json");
    }

    private void persistToDisk() {
        if (workspaceService == null) return;
        try {
            Path file = getStorageFile();
            if (!Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), new ArrayList<>(records.values()));
        } catch (IOException e) {
            log.warn("Failed to persist idempotency records to disk: {}", e.getMessage());
        }
    }
}
