package com.mft.integration.orchestrator;

import com.mft.integration.checksum.ChecksumAlgorithm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration definition for an integration transfer route.
 */
public class RouteDefinition {

    @NotBlank(message = "routeId must not be blank")
    private String routeId;

    @NotBlank(message = "sourceConnectionId must not be blank")
    private String sourceConnectionId;

    @NotBlank(message = "destinationConnectionId must not be blank")
    private String destinationConnectionId;

    @NotNull(message = "processingMode must not be null")
    private ProcessingMode processingMode = ProcessingMode.RAW_COPY;

    private String sourceDirectory = "/";
    private String sourcePattern = "^.*\\.(?i)(pdf|docx|doc|xlsx|xls|exl|txt)$";
    private String destinationDirectory = "/";
    private ChecksumAlgorithm checksumAlgorithm = ChecksumAlgorithm.SHA_256;
    private int retryLimit = 3;
    private long retryInitialDelayMs = 500;
    private double retryMultiplier = 2.0;
    private boolean allowReprocessing = false;
    private boolean enabled = true;

    /**
     * Minimum allowed file size in bytes (default: 0 = no minimum).
     * Files below this size are rejected with an audit log.
     */
    private long minFileSize = 0L;

    /**
     * Maximum standard file size threshold in bytes (default: 10 MB = 10,485,760 bytes).
     * Files above this threshold are automatically compressed before transport.
     */
    private long maxStandardFileSize = 10L * 1024L * 1024L;

    /**
     * Whether files exceeding maxStandardFileSize should be automatically compressed.
     */
    private boolean compressLargeFiles = true;

    public RouteDefinition() {
    }

    public RouteDefinition(String routeId, String sourceConnectionId, String destinationConnectionId,
                           ProcessingMode processingMode, String sourceDirectory, String sourcePattern,
                           String destinationDirectory) {
        this.routeId = routeId;
        this.sourceConnectionId = sourceConnectionId;
        this.destinationConnectionId = destinationConnectionId;
        this.processingMode = processingMode;
        this.sourceDirectory = sourceDirectory;
        this.sourcePattern = sourcePattern;
        this.destinationDirectory = destinationDirectory;
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

    public String getDestinationConnectionId() {
        return destinationConnectionId;
    }

    public void setDestinationConnectionId(String destinationConnectionId) {
        this.destinationConnectionId = destinationConnectionId;
    }

    public ProcessingMode getProcessingMode() {
        return processingMode;
    }

    public void setProcessingMode(ProcessingMode processingMode) {
        this.processingMode = processingMode;
    }

    public String getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(String sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public String getSourcePattern() {
        return sourcePattern;
    }

    public void setSourcePattern(String sourcePattern) {
        this.sourcePattern = sourcePattern;
    }

    public String getDestinationDirectory() {
        return destinationDirectory;
    }

    public void setDestinationDirectory(String destinationDirectory) {
        this.destinationDirectory = destinationDirectory;
    }

    public ChecksumAlgorithm getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public void setChecksumAlgorithm(ChecksumAlgorithm checksumAlgorithm) {
        this.checksumAlgorithm = checksumAlgorithm;
    }

    public int getRetryLimit() {
        return retryLimit;
    }

    public void setRetryLimit(int retryLimit) {
        this.retryLimit = retryLimit;
    }

    public long getRetryInitialDelayMs() {
        return retryInitialDelayMs;
    }

    public void setRetryInitialDelayMs(long retryInitialDelayMs) {
        this.retryInitialDelayMs = retryInitialDelayMs;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    public boolean isAllowReprocessing() {
        return allowReprocessing;
    }

    public void setAllowReprocessing(boolean allowReprocessing) {
        this.allowReprocessing = allowReprocessing;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMinFileSize() {
        return minFileSize;
    }

    public void setMinFileSize(long minFileSize) {
        this.minFileSize = minFileSize;
    }

    public long getMaxStandardFileSize() {
        return maxStandardFileSize;
    }

    public void setMaxStandardFileSize(long maxStandardFileSize) {
        this.maxStandardFileSize = maxStandardFileSize;
    }

    public boolean isCompressLargeFiles() {
        return compressLargeFiles;
    }

    public void setCompressLargeFiles(boolean compressLargeFiles) {
        this.compressLargeFiles = compressLargeFiles;
    }

    @Override
    public String toString() {
        return "RouteDefinition{" +
                "routeId='" + routeId + '\'' +
                ", source=" + sourceConnectionId + " -> " + destinationConnectionId +
                ", mode=" + processingMode +
                ", srcDir='" + sourceDirectory + '\'' +
                ", pattern='" + sourcePattern + '\'' +
                ", dstDir='" + destinationDirectory + '\'' +
                ", minSize=" + minFileSize + "B" +
                ", maxStdSize=" + maxStandardFileSize + "B" +
                ", compressLargeFiles=" + compressLargeFiles +
                ", retryLimit=" + retryLimit +
                ", allowReprocessing=" + allowReprocessing +
                ", enabled=" + enabled +
                '}';
    }
}
