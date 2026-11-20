package com.mft.integration.adapter.write;

import com.mft.integration.adapter.model.TransferResult;

import java.nio.file.Path;

/**
 * Adapter interface for outbound file publication to remote destinations (SFTP, etc.).
 * Guarantees atomic publication semantics using temporary staging files and remote renames.
 */
public interface WriteAdapter {

    /**
     * Uploads a local file to a specific remote file path.
     */
    TransferResult uploadFile(String connectionId, Path localFilePath, String remoteFilePath, boolean overwrite);

    /**
     * Uploads a local file to a remote directory under a specified remote filename.
     */
    TransferResult uploadFile(String connectionId, Path localFilePath, String remoteDirectory, String remoteFilename, boolean overwrite);

    /**
     * Checks whether a remote file exists on the destination server.
     */
    boolean remoteFileExists(String connectionId, String remoteFilePath);

    /**
     * Creates a directory on the remote destination server.
     */
    void createDirectory(String connectionId, String remoteDirectory);

    /**
     * Renames or moves a file on the remote destination server.
     */
    void renameRemoteFile(String connectionId, String sourcePath, String destinationPath);

    /**
     * Performs a two-phase atomic publication:
     * 1. Uploads local file to remote destination with staging extension (e.g. filename.part).
     * 2. Renames remote staging file to final published filename.
     *
     * @param connectionId target connection
     * @param localFilePath source local file
     * @param remoteDirectory destination directory
     * @param targetFilename final published filename
     * @param overwrite whether existing destination file may be replaced
     * @return structured transfer result
     */
    TransferResult publishFile(String connectionId, Path localFilePath, String remoteDirectory, String targetFilename, boolean overwrite);
}
