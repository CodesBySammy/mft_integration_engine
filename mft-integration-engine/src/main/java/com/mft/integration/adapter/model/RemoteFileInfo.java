package com.mft.integration.adapter.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable model representing remote file attributes on an SFTP server.
 */
public class RemoteFileInfo {

    private final String connectionId;
    private final String remotePath;
    private final String filename;
    private final long size;
    private final Instant modifiedTime;
    private final boolean isDirectory;
    private final String permissions;

    public RemoteFileInfo(String connectionId, String remotePath, String filename,
                          long size, Instant modifiedTime, boolean isDirectory, String permissions) {
        this.connectionId = connectionId;
        this.remotePath = remotePath;
        this.filename = filename;
        this.size = size;
        this.modifiedTime = modifiedTime;
        this.isDirectory = isDirectory;
        this.permissions = permissions;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    public Instant getModifiedTime() {
        return modifiedTime;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public String getPermissions() {
        return permissions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteFileInfo that = (RemoteFileInfo) o;
        return size == that.size &&
                isDirectory == that.isDirectory &&
                Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(remotePath, that.remotePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, remotePath, size, isDirectory);
    }

    @Override
    public String toString() {
        return "RemoteFileInfo{" +
                "connectionId='" + connectionId + '\'' +
                ", remotePath='" + remotePath + '\'' +
                ", filename='" + filename + '\'' +
                ", size=" + size +
                ", modifiedTime=" + modifiedTime +
                ", isDirectory=" + isDirectory +
                ", permissions='" + permissions + '\'' +
                '}';
    }
}
