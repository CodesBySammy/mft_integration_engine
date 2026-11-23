package com.mft.integration.workspace;

import com.mft.integration.exception.FileProcessingException;
import com.mft.integration.util.PathValidationUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Service managing the controlled local filesystem workspace.
 * Enforces boundary containment to prevent unauthorized local file access.
 */
@Service
public class LocalWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(LocalWorkspaceService.class);

    private final Path rootWorkspacePath;

    public LocalWorkspaceService(@Value("${mft.workspace.root-dir:D:/mft/workspace}") String rootDir) {
        this.rootWorkspacePath = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initialize() {
        try {
            if (!Files.exists(rootWorkspacePath)) {
                Files.createDirectories(rootWorkspacePath);
            }
            for (WorkspaceDirectory dir : WorkspaceDirectory.values()) {
                Path subDir = rootWorkspacePath.resolve(dir.getFolderName());
                if (!Files.exists(subDir)) {
                    Files.createDirectories(subDir);
                }
            }
            log.info("Initialized local MFT workspace at [{}] with all lifecycle subdirectories.", rootWorkspacePath);
        } catch (IOException e) {
            throw new FileProcessingException("WORKSPACE_INIT_FAILED",
                    "Failed to initialize workspace root directory: " + rootWorkspacePath, e);
        }
    }

    public Path getWorkspaceRoot() {
        return rootWorkspacePath;
    }

    public Path getDirectory(WorkspaceDirectory dir) {
        return rootWorkspacePath.resolve(dir.getFolderName());
    }

    /**
     * Resolves a local path within a designated workspace subdirectory.
     * Enforces path containment to reject any path escaping attempt.
     */
    public Path resolvePath(WorkspaceDirectory dir, String filename) {
        return resolvePath(dir, null, filename);
    }

    /**
     * Resolves a local path within a designated workspace subdirectory and optional run-isolated subfolder.
     * Enforces path containment to eliminate concurrent file overwrite races.
     */
    public Path resolvePath(WorkspaceDirectory dir, String subfolder, String filename) {
        Path targetDir = getDirectory(dir);
        if (subfolder != null && !subfolder.isBlank()) {
            targetDir = targetDir.resolve(subfolder);
            try {
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
            } catch (IOException e) {
                throw new FileProcessingException("WORKSPACE_DIR_FAILED", "Failed to create directory: " + targetDir, e);
            }
        }
        Path targetFile = targetDir.resolve(filename);
        return PathValidationUtil.validateLocalWorkspacePath(targetFile, rootWorkspacePath);
    }

    /**
     * Safely moves a corrupted or invalid file into the quarantine directory with reason metadata.
     */
    public Path quarantineFile(Path sourceFile, String runId, String failureReason) {
        if (sourceFile == null || !Files.exists(sourceFile)) {
            log.warn("Cannot quarantine null or non-existent file: {}", sourceFile);
            return null;
        }

        try {
            String subfolder = runId != null && !runId.isBlank() ? runId : "unassigned";
            Path quarantineDir = getDirectory(WorkspaceDirectory.QUARANTINE).resolve(subfolder);
            if (!Files.exists(quarantineDir)) {
                Files.createDirectories(quarantineDir);
            }

            Path quarantinedTarget = quarantineDir.resolve(sourceFile.getFileName());
            Files.move(sourceFile, quarantinedTarget, StandardCopyOption.REPLACE_EXISTING);

            // Write quarantine incident reason file
            Path reasonFile = quarantineDir.resolve(sourceFile.getFileName().toString() + ".reason.txt");
            String reasonContent = String.format("Quarantine Timestamp: %s%nRunId: %s%nReason: %s%nOriginal Path: %s%n",
                    Instant.now(), runId, failureReason, sourceFile);
            Files.writeString(reasonFile, reasonContent);

            log.warn("Quarantined file [{}] to [{}] due to: {}", sourceFile.getFileName(), quarantinedTarget, failureReason);
            return quarantinedTarget;
        } catch (IOException e) {
            log.error("Failed to move file to quarantine [{}]: {}", sourceFile, e.getMessage());
            throw new FileProcessingException("QUARANTINE_FAILED", "Failed to quarantine file: " + sourceFile, e);
        }
    }

    /**
     * Cleans up all files in a designated workspace directory (e.g. TEMP).
     */
    public void cleanDirectory(WorkspaceDirectory dir) {
        Path targetDir = getDirectory(dir);
        try (var stream = Files.newDirectoryStream(targetDir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    Files.deleteIfExists(entry);
                }
            }
            log.debug("Cleaned workspace directory [{}]", dir);
        } catch (IOException e) {
            log.warn("Failed to clean workspace directory [{}]: {}", dir, e.getMessage());
        }
    }
}
