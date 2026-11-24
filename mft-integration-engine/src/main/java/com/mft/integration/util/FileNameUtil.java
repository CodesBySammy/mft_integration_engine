package com.mft.integration.util;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Utility for filename pattern matching using standard regex or glob syntax.
 * Supports patterns like "^.*\\.(?i)(pdf|docx|doc|xlsx|xls|exl|txt)$", "*.txt", or "regex:...".
 */
public final class FileNameUtil {

    private FileNameUtil() {
    }

    /**
     * Matches a filename against a regex or glob pattern.
     * If pattern is null or blank, matches everything.
     */
    public static boolean matchesPattern(String filename, String pattern) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        if (pattern == null || pattern.isBlank() || "*".equals(pattern) || "*.*".equals(pattern)) {
            return true;
        }

        // 1. Explicit regex prefix: "regex:..."
        if (pattern.startsWith("regex:")) {
            try {
                return Pattern.compile(pattern.substring(6)).matcher(filename).matches();
            } catch (Exception e) {
                return false;
            }
        }

        // 2. Implicit regex detection (contains regex tokens like ^, $, |, (?i), (, ))
        if (pattern.startsWith("^") || pattern.endsWith("$") || pattern.contains("|")
                || pattern.contains("(?i)") || (pattern.contains("(") && pattern.contains(")"))) {
            try {
                return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(filename).matches();
            } catch (Exception ignored) {
                // Fall back to glob
            }
        }

        // 3. Standard Java NIO Glob matching
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return matcher.matches(Paths.get(filename));
        } catch (IllegalArgumentException e) {
            // Fallback simple wildcard regex conversion
            String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(filename).matches();
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
