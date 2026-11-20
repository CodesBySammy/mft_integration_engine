package com.mft.integration.adapter.model;

import java.time.Instant;

/**
 * Encapsulates the structured outcome of an upload or publication operation.
 */
public class TransferResult {

    private final boolean success;
    private final String connectionId;
    private final String remoteDirectory;
    private final String filename;
    private final String fullRemotePath;
    private final long bytesTransferred;
    private final Instant timestamp;
    private final String errorMessage;

    public TransferResult(boolean success, String connectionId, String remoteDirectory,
                          String filename, String fullRemotePath, long bytesTransferred,
                          Instant timestamp, String errorMessage) {
        this.success = success;
        this.connectionId = connectionId;
        this.remoteDirectory = remoteDirectory;
        this.filename = filename;
        this.fullRemotePath = fullRemotePath;
        this.bytesTransferred = bytesTransferred;
        this.timestamp = timestamp;
        this.errorMessage = errorMessage;
    }

    public static TransferResult success(String connectionId, String remoteDirectory,
                                         String filename, String fullRemotePath, long bytesTransferred) {
        return new TransferResult(true, connectionId, remoteDirectory, filename, fullRemotePath,
                bytesTransferred, Instant.now(), null);
    }

    public static TransferResult failure(String connectionId, String remoteDirectory,
                                         String filename, String fullRemotePath, String errorMessage) {
        return new TransferResult(false, connectionId, remoteDirectory, filename, fullRemotePath,
                0L, Instant.now(), errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getRemoteDirectory() {
        return remoteDirectory;
    }

    public String getFilename() {
        return filename;
    }

    public String getFullRemotePath() {
        return fullRemotePath;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "TransferResult{" +
                "success=" + success +
                ", connectionId='" + connectionId + '\'' +
                ", fullRemotePath='" + fullRemotePath + '\'' +
                ", bytesTransferred=" + bytesTransferred +
                ", timestamp=" + timestamp +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
