package com.mft.integration.api.dto;

import com.mft.integration.orchestrator.RouteExecutionResult;

import java.time.Instant;
import java.util.List;

/**
 * Execution result DTO returned by route triggers.
 */
public class RouteExecutionResponseDto {

    private final String runId;
    private final String routeId;
    private final boolean success;
    private final int filesDiscovered;
    private final int filesTransferred;
    private final int filesSkipped;
    private final int filesFailed;
    private final Instant startTime;
    private final Instant endTime;
    private final String message;
    private final List<TransferRunDto> itemRuns;

    public RouteExecutionResponseDto(RouteExecutionResult result) {
        this.runId = result.getRunId();
        this.routeId = result.getRouteId();
        this.success = result.isSuccess();
        this.filesDiscovered = result.getFilesDiscovered();
        this.filesTransferred = result.getFilesTransferred();
        this.filesSkipped = result.getFilesSkipped();
        this.filesFailed = result.getFilesFailed();
        this.startTime = result.getStartTime();
        this.endTime = result.getEndTime();
        this.message = result.getMessage();
        this.itemRuns = result.getItemRuns().stream()
                .map(TransferRunDto::new)
                .toList();
    }

    public String getRunId() {
        return runId;
    }

    public String getRouteId() {
        return routeId;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getFilesDiscovered() {
        return filesDiscovered;
    }

    public int getFilesTransferred() {
        return filesTransferred;
    }

    public int getFilesSkipped() {
        return filesSkipped;
    }

    public int getFilesFailed() {
        return filesFailed;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public String getMessage() {
        return message;
    }

    public List<TransferRunDto> getItemRuns() {
        return itemRuns;
    }
}
