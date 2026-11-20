package com.mft.integration.util;

import com.mft.integration.exception.ValidationException;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility for sanitizing and validating remote and local paths against path traversal attacks.
 */
public final class PathValidationUtil {

    private PathValidationUtil() {
    }

    /**
     * Validates and normalizes an SFTP remote path.
     * Rejects paths with directory traversal sequences (..) or illegal characters.
     */
    public static String validateAndNormalizeRemotePath(String remotePath) {
        if (remotePath == null || remotePath.isBlank()) {
            throw new ValidationException("PATH_EMPTY", "Remote path must not be null or blank.");
        }

        // Standardize forward slashes
        String normalized = remotePath.replace('\\', '/').trim();

        // Check for directory traversal
        if (normalized.contains("/../") || normalized.endsWith("/..") || normalized.startsWith("../") || normalized.equals("..")) {
            throw new ValidationException("PATH_TRAVERSAL_DETECTED",
                    "Path traversal attempt detected in remote path: " + remotePath);
        }

        // Collapse duplicate slashes
        normalized = normalized.replaceAll("/+", "/");

        return normalized;
    }

    /**
     * Combines a remote base directory and relative path safely.
     */
    public static String combineRemotePath(String baseDir, String subPath) {
        String base = validateAndNormalizeRemotePath(baseDir != null && !baseDir.isBlank() ? baseDir : "/");
        String sub = subPath != null ? subPath.replace('\\', '/').trim() : "";

        if (sub.startsWith("/")) {
            sub = sub.substring(1);
        }

        if (sub.contains("/../") || sub.endsWith("/..") || sub.startsWith("../") || sub.equals("..")) {
            throw new ValidationException("PATH_TRAVERSAL_DETECTED",
                    "Path traversal attempt in sub-path: " + subPath);
        }

        if (base.endsWith("/")) {
            return base + sub;
        } else {
            return base + "/" + sub;
        }
    }

    /**
     * Ensures a local file path stays strictly within the designated parent workspace directory.
     */
    public static Path validateLocalWorkspacePath(Path localPath, Path allowedBaseDirectory) {
        if (localPath == null || allowedBaseDirectory == null) {
            throw new ValidationException("PATH_EMPTY", "Local paths must not be null.");
        }

        Path normalizedBase = allowedBaseDirectory.toAbsolutePath().normalize();
        Path normalizedTarget = localPath.toAbsolutePath().normalize();

        if (!normalizedTarget.startsWith(normalizedBase)) {
            throw new ValidationException("PATH_ESCAPE_DETECTED",
                    "Access denied: target path [" + normalizedTarget + "] is outside allowed workspace [" + normalizedBase + "].");
        }

        return normalizedTarget;
    }
}
