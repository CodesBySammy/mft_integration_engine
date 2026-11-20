package com.mft.integration.api.dto;

/**
 * Request payload for triggering route execution.
 */
public class RouteExecutionRequest {

    private String patternOverride;

    public RouteExecutionRequest() {
    }

    public RouteExecutionRequest(String patternOverride) {
        this.patternOverride = patternOverride;
    }

    public String getPatternOverride() {
        return patternOverride;
    }

    public void setPatternOverride(String patternOverride) {
        this.patternOverride = patternOverride;
    }
}
