package com.mft.integration.api.dto;

import com.mft.integration.checksum.ChecksumAlgorithm;
import com.mft.integration.orchestrator.ProcessingMode;
import com.mft.integration.orchestrator.RouteDefinition;

/**
 * Safe presentation DTO of a configured transfer route.
 */
public class RouteDto {

    private final String routeId;
    private final String sourceConnectionId;
    private final String destinationConnectionId;
    private final ProcessingMode processingMode;
    private final String sourceDirectory;
    private final String sourcePattern;
    private final String destinationDirectory;
    private final ChecksumAlgorithm checksumAlgorithm;
    private final int retryLimit;
    private final boolean allowReprocessing;
    private final boolean enabled;

    public RouteDto(RouteDefinition route) {
        this.routeId = route.getRouteId();
        this.sourceConnectionId = route.getSourceConnectionId();
        this.destinationConnectionId = route.getDestinationConnectionId();
        this.processingMode = route.getProcessingMode();
        this.sourceDirectory = route.getSourceDirectory();
        this.sourcePattern = route.getSourcePattern();
        this.destinationDirectory = route.getDestinationDirectory();
        this.checksumAlgorithm = route.getChecksumAlgorithm();
        this.retryLimit = route.getRetryLimit();
        this.allowReprocessing = route.isAllowReprocessing();
        this.enabled = route.isEnabled();
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

    public ProcessingMode getProcessingMode() {
        return processingMode;
    }

    public String getSourceDirectory() {
        return sourceDirectory;
    }

    public String getSourcePattern() {
        return sourcePattern;
    }

    public String getDestinationDirectory() {
        return destinationDirectory;
    }

    public ChecksumAlgorithm getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public int getRetryLimit() {
        return retryLimit;
    }

    public boolean isAllowReprocessing() {
        return allowReprocessing;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
