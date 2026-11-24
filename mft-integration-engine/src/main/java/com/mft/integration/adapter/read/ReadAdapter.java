package com.mft.integration.adapter.read;

import com.mft.integration.adapter.model.RemoteFileInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Adapter interface for interacting with remote file sources (SFTP, etc.).
 * Implementations must be completely independent of orchestration and REST layers.
 */
public interface ReadAdapter {

    /**
     * Lists all files and directories in a remote directory.
     */
    List<RemoteFileInfo> listFiles(String connectionId, String remoteDirectory);

    /**
     * Discovers ready-to-process files in a remote directory matching a pattern.
     * Skips subdirectories and in-flight temporary (.part / .tmp) files.
     */
    List<RemoteFileInfo> discoverFiles(String connectionId, String remoteDirectory, String filePattern);

    /**
     * Downloads a remote file to a local destination.
     * Downloads to a temporary local file first, then atomically renames to the target path.
     *
     * @param connectionId connection identifier
     * @param remoteFilePath absolute or relative remote path
     * @param localDestinationPath target local destination path
     * @param overwrite whether an existing local file should be overwritten
     * @return Path to the successfully downloaded local file
     */
    Path downloadFile(String connectionId, String remoteFilePath, Path localDestinationPath, boolean overwrite);

    /**
     * Checks whether a remote file or directory exists.
     */
    boolean fileExists(String connectionId, String remoteFilePath);

    /**
     * Retrieves remote metadata for a specific file.
     */
    Optional<RemoteFileInfo> getFileMetadata(String connectionId, String remoteFilePath);

    /**
     * Performs a live connection handshake test to verify server reachability and credentials.
     *
     * @param connectionId connection identifier
     * @return true if connection succeeds
     */
    boolean testConnection(String connectionId);

    /**
     * Deletes a file on the remote source server after successful verified transfer.
     */
    void deleteFile(String connectionId, String remoteFilePath);

    /**
     * Atomically moves or archives a file on the remote source server.
     */
    void moveFile(String connectionId, String sourcePath, String destinationPath);
}
