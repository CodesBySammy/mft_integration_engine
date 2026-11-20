package com.mft.integration.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for recording and querying structured audit events.
 * Never logs credentials or raw file payloads.
 * Delegates storage to AuditRepository.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void recordEvent(String runId, String routeId, String operation,
                            String sourceFile, String destinationFile, String status,
                            String errorCode, String details) {
        AuditEvent event = new AuditEvent(runId, routeId, operation, sourceFile, destinationFile, status, errorCode, details);
        auditRepository.save(event);

        log.info("AUDIT: runId=[{}] routeId=[{}] op=[{}] src=[{}] dst=[{}] status=[{}] err=[{}]",
                runId, routeId, operation, sourceFile, destinationFile, status, errorCode != null ? errorCode : "NONE");
    }

    public List<AuditEvent> getEventsForRun(String runId) {
        return auditRepository.findByRunId(runId);
    }

    public List<AuditEvent> getEventsForRoute(String routeId) {
        return auditRepository.findByRouteId(routeId);
    }

    public List<AuditEvent> getAllEvents() {
        return auditRepository.findAll();
    }
}
