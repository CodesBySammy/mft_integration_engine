package com.mft.integration.api.dto;

import com.mft.integration.status.TransferRun;
import com.mft.integration.status.TransferStatus;

import java.time.Instant;

/**
 * TransferRun lifecycle presentation DTO.
 */
public class TransferRunDto {

    private final String runId;
    private final String routeId;
    private final String sourceConnectionId;
    private final String destinationConnectionId;
    private final String sourceFilename;
    private final String destinationFilename;
    private final TransferStatus status;
    private final long bytesTransferred;
    private final long recordCount;
    private final String sourceChecksum;
    private final String outputChecksum;
    private final Instant startTime;
    private final Instant endTime;
    private final String errorCode;
    private final String errorMessage;

    public TransferRunDto(TransferRun run) {
        this.runId = run.getRunId();
        this.routeId = run.getRouteId();
        this.sourceConnectionId = run.getSourceConnection();
        this.destinationConnectionId = run.getDestinationConnection();
        this.sourceFilename = run.getSourceFilename();
        this.destinationFilename = run.getDestinationFilename();
        this.status = run.getStatus();
        this.bytesTransferred = run.getBytesTransferred();
        this.recordCount = run.getRecordCount();
        this.sourceChecksum = run.getSourceChecksum();
        this.outputChecksum = run.getOutputChecksum();
        this.startTime = run.getStartTime();
        this.endTime = run.getEndTime();
        this.errorCode = run.getErrorCode();
        this.errorMessage = run.getErrorMessage();
    }

    public String getRunId() {
        return runId;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getSourceConnectionId() {
        return sourceConnectionId;
    }

    public String getDestinationConnectionId() {
        return destinationConnectionId;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public String getDestinationFilename() {
        return destinationFilename;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public long getRecordCount() {
        return recordCount;
    }

    public String getSourceChecksum() {
        return sourceChecksum;
    }

    public String getOutputChecksum() {
        return outputChecksum;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
