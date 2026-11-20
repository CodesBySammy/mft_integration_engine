package com.mft.integration.adapter.read;

import com.mft.integration.adapter.model.RemoteFileInfo;
import com.mft.integration.config.SftpSessionFactoryRegistry;
import com.mft.integration.exception.MftException;
import com.mft.integration.exception.SftpConnectionException;
import com.mft.integration.exception.ValidationException;
import com.mft.integration.util.FileNameUtil;
import com.mft.integration.util.PathValidationUtil;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spring Integration SFTP implementation of the ReadAdapter interface.
 * Uses Apache MINA SSHD through Spring Integration session pooling.
 */
@Component
public class SftpReadAdapter implements ReadAdapter {

    private static final Logger log = LoggerFactory.getLogger(SftpReadAdapter.class);

    private final SftpSessionFactoryRegistry sessionFactoryRegistry;

    public SftpReadAdapter(SftpSessionFactoryRegistry sessionFactoryRegistry) {
        this.sessionFactoryRegistry = sessionFactoryRegistry;
    }

    @Override
    public List<RemoteFileInfo> listFiles(String connectionId, String remoteDirectory) {
        String normalizedDir = PathValidationUtil.validateAndNormalizeRemotePath(remoteDirectory);
        log.debug("Listing files on connection [{}] in directory [{}]", connectionId, normalizedDir);

        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            SftpClient.DirEntry[] entries = session.list(normalizedDir);
            List<RemoteFileInfo> result = new ArrayList<>();

            if (entries != null) {
                for (SftpClient.DirEntry entry : entries) {
                    String filename = entry.getFilename();
                    if (".".equals(filename) || "..".equals(filename)) {
                        continue;
                    }
                    result.add(mapDirEntry(connectionId, normalizedDir, entry));
                }
            }
            log.debug("Listed {} items on [{}] in directory [{}]", result.size(), connectionId, normalizedDir);
            return result;
        } catch (IOException e) {
            log.error("Failed to list files on [{}] in [{}]: {}", connectionId, normalizedDir, e.getMessage());
            throw new SftpConnectionException(connectionId, "Failed to list directory: " + normalizedDir, e);
        }
    }

    @Override
    public List<RemoteFileInfo> discoverFiles(String connectionId, String remoteDirectory, String filePattern) {
        String normalizedDir = PathValidationUtil.validateAndNormalizeRemotePath(remoteDirectory);
        log.debug("Discovering files on connection [{}] in [{}] matching pattern [{}]", connectionId, normalizedDir, filePattern);

        List<RemoteFileInfo> allEntries = listFiles(connectionId, normalizedDir);
        List<RemoteFileInfo> matchingFiles = new ArrayList<>();

        for (RemoteFileInfo entry : allEntries) {
            // Discard directories
            if (entry.isDirectory()) {
                continue;
            }
            // Discard partial and temporary files
            if (FileNameUtil.isTemporaryFile(entry.getFilename())) {
                log.debug("Skipping temporary/in-flight file: {}", entry.getFilename());
                continue;
            }
            // Check glob pattern
            if (FileNameUtil.matchesPattern(entry.getFilename(), filePattern)) {
                matchingFiles.add(entry);
            }
        }

        log.info("Discovered {} candidate file(s) on [{}] matching pattern [{}]", matchingFiles.size(), connectionId, filePattern);
        return matchingFiles;
    }

    @Override
    public Path downloadFile(String connectionId, String remoteFilePath, Path localDestinationPath, boolean overwrite) {
        String normalizedRemote = PathValidationUtil.validateAndNormalizeRemotePath(remoteFilePath);
        if (localDestinationPath == null) {
            throw new ValidationException("PATH_EMPTY", "Local destination path must not be null.");
        }

        log.info("Starting download: connection=[{}], remote=[{}], local=[{}]", connectionId, normalizedRemote, localDestinationPath);

        // Check overwrite protection
        if (Files.exists(localDestinationPath) && !overwrite) {
            throw new ValidationException("FILE_ALREADY_EXISTS",
                    "Local file already exists and overwrite is disabled: " + localDestinationPath);
        }

        try {
            // Ensure local directory exists
            Path parent = localDestinationPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // Staging temporary file to prevent processing partial downloads
            Path tempLocalFile = localDestinationPath.resolveSibling(localDestinationPath.getFileName().toString() + ".download.tmp");

            try (Session<SftpClient.DirEntry> session = openSession(connectionId);
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempLocalFile))) {

                if (!session.exists(normalizedRemote)) {
                    throw new MftException("REMOTE_FILE_NOT_FOUND",
                            "Remote file [" + normalizedRemote + "] not found on connection [" + connectionId + "].");
                }

                session.read(normalizedRemote, out);
            }

            // Atomic move from temporary file to final target destination
            Files.move(tempLocalFile, localDestinationPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            log.info("Download completed successfully: [{}] -> [{}] ({} bytes)",
                    normalizedRemote, localDestinationPath, Files.size(localDestinationPath));

            return localDestinationPath;
        } catch (IOException e) {
            log.error("Download failed for [{}] from [{}]: {}", normalizedRemote, connectionId, e.getMessage());
            throw new SftpConnectionException(connectionId, "Failed to download remote file: " + normalizedRemote, e);
        }
    }

    @Override
    public boolean fileExists(String connectionId, String remoteFilePath) {
        String normalized = PathValidationUtil.validateAndNormalizeRemotePath(remoteFilePath);
        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            return session.exists(normalized);
        } catch (IOException e) {
            log.error("Failed to check remote file existence on [{}] for [{}]: {}", connectionId, normalized, e.getMessage());
            throw new SftpConnectionException(connectionId, "Failed to check file existence: " + normalized, e);
        }
    }

    @Override
    public Optional<RemoteFileInfo> getFileMetadata(String connectionId, String remoteFilePath) {
        String normalized = PathValidationUtil.validateAndNormalizeRemotePath(remoteFilePath);
        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            if (!session.exists(normalized)) {
                return Optional.empty();
            }

            SftpClient.DirEntry[] list = session.list(normalized);
            if (list != null && list.length > 0) {
                return Optional.of(mapDirEntry(connectionId, normalized, list[0]));
            }
            return Optional.empty();
        } catch (IOException e) {
            log.error("Failed to retrieve metadata on [{}] for [{}]: {}", connectionId, normalized, e.getMessage());
            throw new SftpConnectionException(connectionId, "Failed to get metadata: " + normalized, e);
        }
    }

    @Override
    public boolean testConnection(String connectionId) {
        log.info("Testing SFTP connectivity for connection [{}]", connectionId);
        try (Session<SftpClient.DirEntry> session = openSession(connectionId)) {
            boolean open = session.isOpen();
            log.info("SFTP connection test for [{}] succeeded (session open: {})", connectionId, open);
            return open;
        } catch (Exception e) {
            log.warn("SFTP connection test for [{}] failed: {}", connectionId, e.getMessage());
            throw new SftpConnectionException(connectionId, "SFTP handshake failed: " + e.getMessage(), e);
        }
    }

    private Session<SftpClient.DirEntry> openSession(String connectionId) {
        try {
            return sessionFactoryRegistry.getSessionFactory(connectionId).getSession();
        } catch (Exception e) {
            log.error("Unable to obtain SFTP session for connection [{}]: {}", connectionId, e.getMessage());
            throw new SftpConnectionException(connectionId, "Could not open SFTP session: " + e.getMessage(), e);
        }
    }

    private RemoteFileInfo mapDirEntry(String connectionId, String parentDir, SftpClient.DirEntry entry) {
        String fullPath = PathValidationUtil.combineRemotePath(parentDir, entry.getFilename());
        SftpClient.Attributes attrs = entry.getAttributes();

        long size = attrs != null ? attrs.getSize() : 0L;
        boolean isDir = attrs != null && attrs.isDirectory();
        Instant modified = attrs != null && attrs.getModifyTime() != null ?
                attrs.getModifyTime().toInstant() : Instant.now();
        String perms = attrs != null ? String.valueOf(attrs.getPermissions()) : "unknown";

        return new RemoteFileInfo(connectionId, fullPath, entry.getFilename(), size, modified, isDir, perms);
    }
}
