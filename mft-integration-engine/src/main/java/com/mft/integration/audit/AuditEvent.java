package com.mft.integration.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Audit log event capturing immutable operational actions without exposing secrets or payload contents.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditEvent {

    private Instant timestamp;
    private String runId;
    private String routeId;
    private String connectionId;
    private String sourceConnectionId;
    private String destinationConnectionId;
    private String sourceAddress;
    private String destinationAddress;
    private String operation;
    private String sourceFile;
    private String destinationFile;
    private Long fileSize;
    private String checksum;
    private Long durationMs;
    private String status;
    private String errorCode;
    private String details;

    public AuditEvent() {
        this.timestamp = Instant.now();
    }

    public AuditEvent(String runId, String routeId, String operation, String sourceFile,
                      String destinationFile, String status, String errorCode, String details) {
        this(Instant.now(), runId, routeId, null, null, null, operation, sourceFile, destinationFile, status, errorCode, details);
    }

    public AuditEvent(String runId, String routeId, String sourceConnectionId, String destinationConnectionId,
                      String operation, String sourceFile, String destinationFile, String status,
                      String errorCode, String details) {
        this(Instant.now(), runId, routeId,
                (sourceConnectionId != null && destinationConnectionId != null)
                        ? sourceConnectionId + " -> " + destinationConnectionId
                        : (sourceConnectionId != null ? sourceConnectionId : destinationConnectionId),
                sourceConnectionId, destinationConnectionId,
                operation, sourceFile, destinationFile, status, errorCode, details);
    }

    public AuditEvent(Instant timestamp, String runId, String routeId, String connectionId,
                      String sourceConnectionId, String destinationConnectionId,
                      String operation, String sourceFile, String destinationFile,
                      String status, String errorCode, String details) {
        this(timestamp, runId, routeId, connectionId, sourceConnectionId, destinationConnectionId,
                null, null, operation, sourceFile, destinationFile, null, null, null, status, errorCode, details);
    }

    @JsonCreator
    public AuditEvent(@JsonProperty("timestamp") Instant timestamp,
                      @JsonProperty("runId") String runId,
                      @JsonProperty("routeId") String routeId,
                      @JsonProperty("connectionId") String connectionId,
                      @JsonProperty("sourceConnectionId") String sourceConnectionId,
                      @JsonProperty("destinationConnectionId") String destinationConnectionId,
                      @JsonProperty("sourceAddress") String sourceAddress,
                      @JsonProperty("destinationAddress") String destinationAddress,
                      @JsonProperty("operation") String operation,
                      @JsonProperty("sourceFile") String sourceFile,
                      @JsonProperty("destinationFile") String destinationFile,
                      @JsonProperty("fileSize") Long fileSize,
                      @JsonProperty("checksum") String checksum,
                      @JsonProperty("durationMs") Long durationMs,
                      @JsonProperty("status") String status,
                      @JsonProperty("errorCode") String errorCode,
                      @JsonProperty("details") String details) {
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.runId = runId;
        this.routeId = routeId;
        this.sourceConnectionId = sourceConnectionId;
        this.destinationConnectionId = destinationConnectionId;
        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;
        this.connectionId = connectionId != null ? connectionId :
                (sourceConnectionId != null && destinationConnectionId != null
                        ? sourceConnectionId + " -> " + destinationConnectionId
                        : (sourceConnectionId != null ? sourceConnectionId : destinationConnectionId));
        this.operation = operation;
        this.sourceFile = sourceFile;
        this.destinationFile = destinationFile;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.durationMs = durationMs;
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

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getSourceConnectionId() {
        return sourceConnectionId;
    }

    public void setSourceConnectionId(String sourceConnectionId) {
        this.sourceConnectionId = sourceConnectionId;
    }

    public String getDestinationConnectionId() {
        return destinationConnectionId;
    }

    public void setDestinationConnectionId(String destinationConnectionId) {
        this.destinationConnectionId = destinationConnectionId;
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

    public String getSourceAddress() {
        return sourceAddress;
    }

    public void setSourceAddress(String sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "timestamp=" + timestamp +
                ", runId='" + runId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", connectionId='" + connectionId + '\'' +
                ", operation='" + operation + '\'' +
                ", sourceFile='" + sourceFile + '\'' +
                ", destinationFile='" + destinationFile + '\'' +
                ", status='" + status + '\'' +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
