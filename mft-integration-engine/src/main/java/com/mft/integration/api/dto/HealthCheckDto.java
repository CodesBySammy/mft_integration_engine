package com.mft.integration.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Overall health check status for the MFT Integration Engine.
 */
public class HealthCheckDto {

    private final String status;
    private final String applicationName;
    private final int activeConnections;
    private final int activeRoutes;
    private final boolean workspaceReady;
    private final Instant timestamp;
    private final Map<String, Object> details;

    public HealthCheckDto(String status, String applicationName, int activeConnections,
                          int activeRoutes, boolean workspaceReady, Map<String, Object> details) {
        this.status = status;
        this.applicationName = applicationName;
        this.activeConnections = activeConnections;
        this.activeRoutes = activeRoutes;
        this.workspaceReady = workspaceReady;
        this.timestamp = Instant.now();
        this.details = details;
    }

    public String getStatus() {
        return status;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public int getActiveConnections() {
        return activeConnections;
    }

    public int getActiveRoutes() {
        return activeRoutes;
    }

    public boolean isWorkspaceReady() {
        return workspaceReady;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
