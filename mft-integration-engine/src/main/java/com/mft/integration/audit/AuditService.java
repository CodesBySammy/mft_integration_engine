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

    public void recordEvent(String runId, String routeId, String connectionId,
                            String operation, String sourceFile, String destinationFile, String status,
                            String errorCode, String details) {
        AuditEvent event = new AuditEvent(java.time.Instant.now(), runId, routeId, connectionId, null, null,
                operation, sourceFile, destinationFile, status, errorCode, details);
        auditRepository.save(event);

        logAudit(event);
    }

    public void recordDetailedEvent(String runId, String routeId, String sourceConn, String destConn,
                                   String sourceAddr, String destAddr, String operation,
                                   String sourceFile, String destinationFile, Long fileSize,
                                   String checksum, Long durationMs, String status,
                                   String errorCode, String details) {
        AuditEvent event = new AuditEvent(
                java.time.Instant.now(), runId, routeId,
                (sourceConn != null && destConn != null) ? sourceConn + " -> " + destConn : (sourceConn != null ? sourceConn : destConn),
                sourceConn, destConn, sourceAddr, destAddr, operation, sourceFile, destinationFile,
                fileSize, checksum, durationMs, status, errorCode, details
        );
        auditRepository.save(event);
        logAudit(event);
    }

    public void recordEvent(String runId, String routeId, String sourceConnectionId, String destinationConnectionId,
                            String operation, String sourceFile, String destinationFile, String status,
                            String errorCode, String details) {
        AuditEvent event = new AuditEvent(runId, routeId, sourceConnectionId, destinationConnectionId,
                operation, sourceFile, destinationFile, status, errorCode, details);
        auditRepository.save(event);

        logAudit(event);
    }

    private void logAudit(AuditEvent event) {
        String err = event.getErrorCode() != null ? event.getErrorCode() : "NONE";
        String sizeStr = event.getFileSize() != null ? event.getFileSize() + " B" : "N/A";
        String chk = event.getChecksum() != null ? event.getChecksum().substring(0, Math.min(12, event.getChecksum().length())) + "..." : "N/A";

        log.info("[AUDIT EVENT] [{}] runId=[{}] route=[{}] conn=[{}] src=[{}] dst=[{}] size=[{}] sha256=[{}] status=[{}] err=[{}] details=[{}]",
                event.getOperation(),
                event.getRunId(),
                event.getRouteId(),
                event.getConnectionId(),
                event.getSourceFile() != null ? event.getSourceFile() : "-",
                event.getDestinationFile() != null ? event.getDestinationFile() : "-",
                sizeStr,
                chk,
                event.getStatus(),
                err,
                event.getDetails() != null ? event.getDetails() : "");
    }

    public void recordEvent(String runId, String routeId, String operation,
                            String sourceFile, String destinationFile, String status,
                            String errorCode, String details) {
        recordEvent(runId, routeId, null, null, operation, sourceFile, destinationFile, status, errorCode, details);
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
