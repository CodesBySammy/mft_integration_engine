package com.mft.integration.api.dto;

import com.mft.integration.audit.AuditEvent;

import java.time.Instant;

/**
 * Sanitized presentation DTO for an audit event.
 */
public class AuditEventDto {

    private final Instant timestamp;
    private final String runId;
    private final String routeId;
    private final String connectionId;
    private final String sourceConnectionId;
    private final String destinationConnectionId;
    private final String operation;
    private final String sourceFile;
    private final String destinationFile;
    private final String status;
    private final String errorCode;
    private final String details;

    public AuditEventDto(AuditEvent event) {
        this.timestamp = event.getTimestamp();
        this.runId = event.getRunId();
        this.routeId = event.getRouteId();
        this.connectionId = event.getConnectionId();
        this.sourceConnectionId = event.getSourceConnectionId();
        this.destinationConnectionId = event.getDestinationConnectionId();
        this.operation = event.getOperation();
        this.sourceFile = event.getSourceFile();
        this.destinationFile = event.getDestinationFile();
        this.status = event.getStatus();
        this.errorCode = event.getErrorCode();
        this.details = event.getDetails();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getRunId() {
        return runId;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getSourceConnectionId() {
        return sourceConnectionId;
    }

    public String getDestinationConnectionId() {
        return destinationConnectionId;
    }

    public String getOperation() {
        return operation;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getDestinationFile() {
        return destinationFile;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }
}
