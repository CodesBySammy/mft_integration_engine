package com.mft.integration.audit;

import java.util.List;

/**
 * Repository interface for persistent storage and retrieval of transfer audit events.
 */
public interface AuditRepository {

    AuditEvent save(AuditEvent event);

    List<AuditEvent> findByRunId(String runId);

    List<AuditEvent> findByRouteId(String routeId);

    List<AuditEvent> findAll();

    void clear();
}
