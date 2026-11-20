package com.mft.integration.api.dto;

import com.mft.integration.adapter.model.RemoteFileInfo;

import java.time.Instant;

/**
 * Information regarding a remote file discovered or queried on an SFTP server.
 */
public class RemoteFileDto {

    private final String connectionId;
    private final String filename;
    private final String remotePath;
    private final long size;
    private final Instant modifiedTime;
    private final boolean directory;
    private final String permissions;

    public RemoteFileDto(RemoteFileInfo info) {
        this.connectionId = info.getConnectionId();
        this.filename = info.getFilename();
        this.remotePath = info.getRemotePath();
        this.size = info.getSize();
        this.modifiedTime = info.getModifiedTime();
        this.directory = info.isDirectory();
        this.permissions = info.getPermissions();
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getFilename() {
        return filename;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public long getSize() {
        return size;
    }

    public Instant getModifiedTime() {
        return modifiedTime;
    }

    public boolean isDirectory() {
        return directory;
    }

    public String getPermissions() {
        return permissions;
    }
}
