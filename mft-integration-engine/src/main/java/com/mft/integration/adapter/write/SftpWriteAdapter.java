package com.mft.integration.adapter.write;

import com.mft.integration.adapter.model.TransferResult;
import com.mft.integration.config.SftpSessionFactoryRegistry;
import com.mft.integration.exception.MftException;
import com.mft.integration.exception.SftpConnectionException;
import com.mft.integration.exception.ValidationException;
import com.mft.integration.util.PathValidationUtil;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Spring Integration SFTP implementation of the WriteAdapter interface.
 * Implements safe staging (.part) and atomic rename publication.
 */
@Component
public class SftpWriteAdapter implements WriteAdapter {

    private static final Logger log = LoggerFactory.getLogger(SftpWriteAdapter.class);
    private static final String STAGING_EXTENSION = ".part";

    private final SftpSessionFactoryRegistry sessionFactoryRegistry;

    public SftpWriteAdapter(SftpSessionFactoryRegistry sessionFactoryRegistry) {
        this.sessionFactoryRegistry = sessionFactoryRegistry;
    }

    @Override
    public TransferResult uploadFile(String connectionId, Path localFilePath, String remoteFilePath, boolean overwrite) {
        String normalizedRemote = PathValidationUtil.validateAndNormalizeRemotePath(remoteFilePath);
        validateLocalFile(localFilePath);

        log.info("Uploading local file [{}] to connection [{}] path [{}] (overwrite={})",
                localFilePath, connectionId, normalizedRemote, overwrite);

        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            // Check overwrite protection
            if (session.exists(normalizedRemote) && !overwrite) {
                throw new ValidationException("REMOTE_FILE_ALREADY_EXISTS",
                        "Remote file already exists and overwrite is disabled: " + normalizedRemote);
            }

            // Ensure parent directory exists
            String parentDir = getParentDirectory(normalizedRemote);
            if (!session.exists(parentDir)) {
                log.info("Remote directory [{}] does not exist on [{}]. Creating...", parentDir, connectionId);
                createDirectoryWithSession(session, parentDir);
            }

            long fileSize = Files.size(localFilePath);
            try (InputStream in = new BufferedInputStream(Files.newInputStream(localFilePath))) {
                session.write(in, normalizedRemote);
            }

            log.info("Successfully uploaded [{}] to [{}:{}] ({} bytes)",
                    localFilePath.getFileName(), connectionId, normalizedRemote, fileSize);

            String filename = localFilePath.getFileName().toString();
            return TransferResult.success(connectionId, parentDir, filename, normalizedRemote, fileSize);
        } catch (IOException e) {
            log.error("Failed to upload [{}] to [{}:{}]: {}", localFilePath, connectionId, normalizedRemote, e.getMessage());
            throw new SftpConnectionException(connectionId, "Failed to upload file to " + normalizedRemote, e);
        }
    }

    @Override
    public TransferResult uploadFile(String connectionId, Path localFilePath, String remoteDirectory,
                                    String remoteFilename, boolean overwrite) {
        String fullPath = PathValidationUtil.combineRemotePath(remoteDirectory, remoteFilename);
        return uploadFile(connectionId, localFilePath, fullPath, overwrite);
    }

    @Override
    public boolean remoteFileExists(String connectionId, String remoteFilePath) {
        String normalized = PathValidationUtil.validateAndNormalizeRemotePath(remoteFilePath);
        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            return session.exists(normalized);
        } catch (IOException e) {
            log.error("Failed to check remote file existence on [{}] for [{}]: {}", connectionId, normalized, e.getMessage());
            throw new SftpConnectionException(connectionId, "Failed to check file existence: " + normalized, e);
        }
    }

    @Override
    public void createDirectory(String connectionId, String remoteDirectory) {
        String normalized = PathValidationUtil.validateAndNormalizeRemotePath(remoteDirectory);
        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            createDirectoryWithSession(session, normalized);
        } catch (IOException e) {
            log.error("Failed to create remote directory [{}] on [{}]: {}", normalized, connectionId, e.getMessage());
            throw new SftpConnectionException(connectionId, "Failed to create directory: " + normalized, e);
        }
    }

    @Override
    public void renameRemoteFile(String connectionId, String sourcePath, String destinationPath) {
        String normSource = PathValidationUtil.validateAndNormalizeRemotePath(sourcePath);
        String normDest = PathValidationUtil.validateAndNormalizeRemotePath(destinationPath);

        log.info("Renaming remote file on [{}]: [{}] -> [{}]", connectionId, normSource, normDest);

        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            if (!session.exists(normSource)) {
                throw new MftException("REMOTE_SOURCE_NOT_FOUND",
                        "Cannot rename: remote source file does not exist: " + normSource);
            }
            session.rename(normSource, normDest);
            log.info("Successfully renamed remote file on [{}] to [{}]", connectionId, normDest);
        } catch (IOException e) {
            log.error("Failed to rename remote file on [{}] from [{}] to [{}]: {}",
                    connectionId, normSource, normDest, e.getMessage());
            throw new SftpConnectionException(connectionId,
                    "Failed to rename remote file from " + normSource + " to " + normDest, e);
        }
    }

    @Override
    public TransferResult publishFile(String connectionId, Path localFilePath, String remoteDirectory,
                                     String targetFilename, boolean overwrite) {
        validateLocalFile(localFilePath);
        String finalRemotePath = PathValidationUtil.combineRemotePath(remoteDirectory, targetFilename);
        String stagingFilename = targetFilename + STAGING_EXTENSION;
        String stagingRemotePath = PathValidationUtil.combineRemotePath(remoteDirectory, stagingFilename);

        log.info("Starting safe atomic publication: file=[{}] -> destination=[{}] via staging=[{}]",
                localFilePath.getFileName(), finalRemotePath, stagingRemotePath);

        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            // Check overwrite on final target
            if (session.exists(finalRemotePath) && !overwrite) {
                throw new ValidationException("REMOTE_FILE_ALREADY_EXISTS",
                        "Publication target already exists on [" + connectionId + "] and overwrite is false: " + finalRemotePath);
            }

            // Phase 1: Upload to temporary staging filename (.part)
            long fileSize = Files.size(localFilePath);
            String parentDir = getParentDirectory(finalRemotePath);
            if (!session.exists(parentDir)) {
                createDirectoryWithSession(session, parentDir);
            }

            try (InputStream in = new BufferedInputStream(Files.newInputStream(localFilePath))) {
                session.write(in, stagingRemotePath);
            }
            log.debug("Phase 1 upload complete to staging: [{}] ({} bytes)", stagingRemotePath, fileSize);

            // If final file exists and overwrite=true, delete or replace
            if (session.exists(finalRemotePath) && overwrite) {
                log.debug("Removing existing destination file prior to publication rename: [{}]", finalRemotePath);
                session.remove(finalRemotePath);
            }

            // Phase 2: Atomic publication via rename
            session.rename(stagingRemotePath, finalRemotePath);
            log.info("Phase 2 publication rename completed successfully: [{}] -> [{}]", stagingRemotePath, finalRemotePath);

            return TransferResult.success(connectionId, remoteDirectory, targetFilename, finalRemotePath, fileSize);
        } catch (IOException e) {
            log.error("Safe atomic publication failed for [{}] on [{}:{}]: {}",
                    localFilePath, connectionId, finalRemotePath, e.getMessage());
            throw new SftpConnectionException(connectionId, "Publication failed for " + finalRemotePath, e);
        }
    }

    @Override
    public void deleteRemoteFile(String connectionId, String remoteFilePath) {
        String normalized = PathValidationUtil.validateAndNormalizeRemotePath(remoteFilePath);
        log.info("Deleting remote destination file on [{}]: [{}]", connectionId, normalized);
        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            if (session.exists(normalized)) {
                session.remove(normalized);
                log.info("Successfully deleted remote file on [{}]: [{}]", connectionId, normalized);
            }
        } catch (IOException e) {
            log.error("Failed to delete remote file on [{}] for [{}]: {}", connectionId, normalized, e.getMessage());
            throw new SftpConnectionException(connectionId, "Failed to delete file: " + normalized, e);
        }
    }

    private Session<SftpClient.DirEntry> openSession(String connectionId) {
        try {
            return sessionFactoryRegistry.getSessionFactory(connectionId).getSession();
        } catch (Exception e) {
            log.error("Unable to obtain SFTP session for write connection [{}]: {}", connectionId, e.getMessage());
            throw new SftpConnectionException(connectionId, "Could not open SFTP write session: " + e.getMessage(), e);
        }
    }

    private void validateLocalFile(Path path) {
        if (path == null) {
            throw new ValidationException("PATH_EMPTY", "Local file path must not be null.");
        }
        if (!Files.exists(path)) {
            throw new ValidationException("LOCAL_FILE_NOT_FOUND", "Local file does not exist: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new ValidationException("INVALID_LOCAL_FILE", "Local path is a directory, not a file: " + path);
        }
    }

    private String getParentDirectory(String remotePath) {
        int lastSlash = remotePath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return remotePath.substring(0, lastSlash);
    }

    private void createDirectoryWithSession(Session<SftpClient.DirEntry> session, String directoryPath) throws IOException {
        String[] parts = directoryPath.split("/");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            current.append("/").append(part);
            String path = current.toString();
            if (!session.exists(path)) {
                log.debug("Creating remote subdirectory: {}", path);
                session.mkdir(path);
            }
        }
    }
}
