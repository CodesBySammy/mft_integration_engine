package com.mft.integration.orchestrator;

import com.mft.integration.status.TransferRun;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Execution result summarizing an entire route orchestration run.
 */
public class RouteExecutionResult {

    private final String runId;
    private final String routeId;
    private final boolean success;
    private final int filesDiscovered;
    private final int filesTransferred;
    private final int filesFailed;
    private final int filesSkipped;
    private final Instant startTime;
    private final Instant endTime;
    private final String message;
    private final List<TransferRun> itemRuns;

    public RouteExecutionResult(String runId, String routeId, boolean success,
                                int filesDiscovered, int filesTransferred, int filesFailed,
                                Instant startTime, Instant endTime, String message,
                                List<TransferRun> itemRuns) {
        this(runId, routeId, success, filesDiscovered, filesTransferred, filesFailed, 0,
                startTime, endTime, message, itemRuns);
    }

    public RouteExecutionResult(String runId, String routeId, boolean success,
                                int filesDiscovered, int filesTransferred, int filesFailed, int filesSkipped,
                                Instant startTime, Instant endTime, String message,
                                List<TransferRun> itemRuns) {
        this.runId = runId;
        this.routeId = routeId;
        this.success = success;
        this.filesDiscovered = filesDiscovered;
        this.filesTransferred = filesTransferred;
        this.filesFailed = filesFailed;
        this.filesSkipped = filesSkipped;
        this.startTime = startTime;
        this.endTime = endTime;
        this.message = message;
        this.itemRuns = itemRuns != null ? itemRuns : new ArrayList<>();
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

    public int getFilesFailed() {
        return filesFailed;
    }

    public int getFilesSkipped() {
        return filesSkipped;
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

    public List<TransferRun> getItemRuns() {
        return itemRuns;
    }

    @Override
    public String toString() {
        return "RouteExecutionResult{" +
                "runId='" + runId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", success=" + success +
                ", discovered=" + filesDiscovered +
                ", transferred=" + filesTransferred +
                ", failed=" + filesFailed +
                ", skipped=" + filesSkipped +
                ", message='" + message + '\'' +
                '}';
    }
}
