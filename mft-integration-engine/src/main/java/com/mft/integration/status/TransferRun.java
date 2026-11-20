package com.mft.integration.status;

import java.time.Instant;

/**
 * Model representing an individual file transfer execution run within a route.
 */
public class TransferRun {

    private String runId;
    private String routeId;
    private String interfaceId;
    private String sourceConnection;
    private String destinationConnection;
    private String sourceFilename;
    private String destinationFilename;
    private Instant startTime;
    private Instant endTime;
    private TransferStatus status;
    private long recordCount;
    private long bytesTransferred;
    private String sourceChecksum;
    private String outputChecksum;
    private int retryCount;
    private String errorCode;
    private String errorMessage;

    public TransferRun() {
        this.startTime = Instant.now();
        this.status = TransferStatus.DISCOVERED;
    }

    public TransferRun(String runId, String routeId, String sourceConnection, String destinationConnection, String sourceFilename) {
        this.runId = runId;
        this.routeId = routeId;
        this.sourceConnection = sourceConnection;
        this.destinationConnection = destinationConnection;
        this.sourceFilename = sourceFilename;
        this.startTime = Instant.now();
        this.status = TransferStatus.DISCOVERED;
    }

    // Getters and Setters

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

    public String getInterfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(String interfaceId) {
        this.interfaceId = interfaceId;
    }

    public String getSourceConnection() {
        return sourceConnection;
    }

    public void setSourceConnection(String sourceConnection) {
        this.sourceConnection = sourceConnection;
    }

    public String getDestinationConnection() {
        return destinationConnection;
    }

    public void setDestinationConnection(String destinationConnection) {
        this.destinationConnection = destinationConnection;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public String getDestinationFilename() {
        return destinationFilename;
    }

    public void setDestinationFilename(String destinationFilename) {
        this.destinationFilename = destinationFilename;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(long recordCount) {
        this.recordCount = recordCount;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public void setBytesTransferred(long bytesTransferred) {
        this.bytesTransferred = bytesTransferred;
    }

    public String getSourceChecksum() {
        return sourceChecksum;
    }

    public void setSourceChecksum(String sourceChecksum) {
        this.sourceChecksum = sourceChecksum;
    }

    public String getOutputChecksum() {
        return outputChecksum;
    }

    public void setOutputChecksum(String outputChecksum) {
        this.outputChecksum = outputChecksum;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "TransferRun{" +
                "runId='" + runId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", sourceFilename='" + sourceFilename + '\'' +
                ", destinationFilename='" + destinationFilename + '\'' +
                ", status=" + status +
                ", bytesTransferred=" + bytesTransferred +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
