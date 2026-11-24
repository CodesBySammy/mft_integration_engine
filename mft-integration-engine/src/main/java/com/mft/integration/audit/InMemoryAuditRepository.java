package com.mft.integration.audit;

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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe implementation of AuditRepository with disk persistence.
 */
@Repository
public class InMemoryAuditRepository implements AuditRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAuditRepository.class);

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();
    private final LocalWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private com.mft.integration.persistence.DatabasePersistenceService databasePersistenceService;

    public InMemoryAuditRepository() {
        this(null);
    }

    @Autowired(required = false)
    public InMemoryAuditRepository(LocalWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        if (workspaceService == null) return;
        try {
            Path file = getStorageFile();
            if (Files.exists(file) && Files.size(file) > 0) {
                List<AuditEvent> loaded = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
                events.addAll(loaded);
                log.info("Loaded {} historical audit event(s) from persistent disk [{}]", events.size(), file);
            }
        } catch (Exception e) {
            log.warn("Could not load persistent audit history from disk: {}", e.getMessage());
        }
    }

    @Override
    public synchronized AuditEvent save(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("AuditEvent must not be null");
        }
        events.add(event);
        persistToDisk();
        if (databasePersistenceService != null) {
            databasePersistenceService.saveAuditEvent(event);
        }
        return event;
    }

    @Override
    public List<AuditEvent> findByRunId(String runId) {
        if (runId == null) return Collections.emptyList();
        return events.stream()
                .filter(e -> runId.equals(e.getRunId()))
                .toList();
    }

    @Override
    public List<AuditEvent> findByRouteId(String routeId) {
        if (routeId == null) return Collections.emptyList();
        return events.stream()
                .filter(e -> routeId.equals(e.getRouteId()))
                .toList();
    }

    @Override
    public List<AuditEvent> findAll() {
        return Collections.unmodifiableList(events);
    }

    @Override
    public synchronized void clear() {
        events.clear();
        persistToDisk();
    }

    private Path getStorageFile() {
        Path statusDir = workspaceService.getDirectory(WorkspaceDirectory.STATUS);
        return statusDir.resolve("audit_history.json");
    }

    private void persistToDisk() {
        if (workspaceService == null) return;
        try {
            Path file = getStorageFile();
            if (!Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), new ArrayList<>(events));
        } catch (IOException e) {
            log.warn("Failed to persist audit events to disk: {}", e.getMessage());
        }
    }
}
