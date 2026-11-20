package com.mft.integration.util;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;

/**
 * Utility for filename pattern matching using standard glob syntax (e.g. *.txt, TRADE_*.dat).
 */
public final class FileNameUtil {

    private FileNameUtil() {
    }

    /**
     * Matches a filename against a glob pattern.
     * If pattern is null or blank, matches everything (*).
     */
    public static boolean matchesPattern(String filename, String pattern) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        if (pattern == null || pattern.isBlank() || "*".equals(pattern) || "*.*".equals(pattern)) {
            return true;
        }

        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return matcher.matches(Paths.get(filename));
        } catch (IllegalArgumentException e) {
            // Fallback to simple regex conversion if glob syntax fails
            String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
            return filename.matches(regex);
        }
    }

    /**
     * Checks if a file is an in-flight temporary or partial transfer file.
     */
    public static boolean isTemporaryFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".part") || lower.endsWith(".tmp") || lower.startsWith(".");
    }
}
