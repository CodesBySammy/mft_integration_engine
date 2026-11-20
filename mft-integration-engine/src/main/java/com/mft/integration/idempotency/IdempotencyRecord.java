package com.mft.integration.idempotency;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents a persistent record of a completed or in-flight transfer for idempotency checking.
 */
public class IdempotencyRecord {

    private String key;
    private String routeId;
    private String sourceConnectionId;
    private String sourceFilePath;
    private long fileSize;
    private String checksum;
    private String runId;
    private String destinationPath;
    private IdempotencyStatus status;
    private String errorReason;
    private Instant createdAt;
    private Instant updatedAt;

    public IdempotencyRecord() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @JsonCreator
    public IdempotencyRecord(@JsonProperty("key") String key,
                             @JsonProperty("routeId") String routeId,
                             @JsonProperty("sourceConnectionId") String sourceConnectionId,
                             @JsonProperty("sourceFilePath") String sourceFilePath,
                             @JsonProperty("fileSize") long fileSize,
                             @JsonProperty("checksum") String checksum,
                             @JsonProperty("runId") String runId,
                             @JsonProperty("status") IdempotencyStatus status) {
        this.key = key;
        this.routeId = routeId;
        this.sourceConnectionId = sourceConnectionId;
        this.sourceFilePath = sourceFilePath;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.runId = runId;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getSourceConnectionId() {
        return sourceConnectionId;
    }

    public void setSourceConnectionId(String sourceConnectionId) {
        this.sourceConnectionId = sourceConnectionId;
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public void setSourceFilePath(String sourceFilePath) {
        this.sourceFilePath = sourceFilePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public void setDestinationPath(String destinationPath) {
        this.destinationPath = destinationPath;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public void setStatus(IdempotencyStatus status) {
        this.status = status;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "IdempotencyRecord{" +
                "key='" + key + '\'' +
                ", routeId='" + routeId + '\'' +
                ", src='" + sourceFilePath + '\'' +
                ", runId='" + runId + '\'' +
                ", status=" + status +
                '}';
    }
}
