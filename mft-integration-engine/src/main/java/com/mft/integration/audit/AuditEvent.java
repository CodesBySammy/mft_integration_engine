package com.mft.integration.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Audit log event capturing immutable operational actions without exposing secrets or payload contents.
 */
public class AuditEvent {

    private Instant timestamp;
    private String runId;
    private String routeId;
    private String operation;
    private String sourceFile;
    private String destinationFile;
    private String status;
    private String errorCode;
    private String details;

    public AuditEvent() {
        this.timestamp = Instant.now();
    }

    public AuditEvent(String runId, String routeId, String operation, String sourceFile,
                      String destinationFile, String status, String errorCode, String details) {
        this.timestamp = Instant.now();
        this.runId = runId;
        this.routeId = routeId;
        this.operation = operation;
        this.sourceFile = sourceFile;
        this.destinationFile = destinationFile;
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    @JsonCreator
    public AuditEvent(@JsonProperty("timestamp") Instant timestamp,
                      @JsonProperty("runId") String runId,
                      @JsonProperty("routeId") String routeId,
                      @JsonProperty("operation") String operation,
                      @JsonProperty("sourceFile") String sourceFile,
                      @JsonProperty("destinationFile") String destinationFile,
                      @JsonProperty("status") String status,
                      @JsonProperty("errorCode") String errorCode,
                      @JsonProperty("details") String details) {
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.runId = runId;
        this.routeId = routeId;
        this.operation = operation;
        this.sourceFile = sourceFile;
        this.destinationFile = destinationFile;
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getDestinationFile() {
        return destinationFile;
    }

    public void setDestinationFile(String destinationFile) {
        this.destinationFile = destinationFile;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "timestamp=" + timestamp +
                ", runId='" + runId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", operation='" + operation + '\'' +
                ", sourceFile='" + sourceFile + '\'' +
                ", destinationFile='" + destinationFile + '\'' +
                ", status='" + status + '\'' +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
