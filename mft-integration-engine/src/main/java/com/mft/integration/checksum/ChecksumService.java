package com.mft.integration.checksum;

import com.mft.integration.exception.FileProcessingException;
import com.mft.integration.exception.IntegrityException;
import com.mft.integration.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Service for computing, validating, and managing SHA-256 / MD5 checksums
 * and companion verification files.
 */
@Service
public class ChecksumService {

    private static final Logger log = LoggerFactory.getLogger(ChecksumService.class);
    private static final int BUFFER_SIZE = 65536; // 64 KB buffer for streaming

    /**
     * Calculates the cryptographic checksum for a local file.
     */
    public String calculateChecksum(Path filePath, ChecksumAlgorithm algorithm) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new ValidationException("FILE_NOT_FOUND", "Cannot calculate checksum: file does not exist: " + filePath);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.getAlgorithmName());
            try (InputStream in = new BufferedInputStream(Files.newInputStream(filePath), BUFFER_SIZE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            String hex = HexFormat.of().formatHex(digest.digest());
            log.debug("Computed {} for [{}]: {}", algorithm, filePath.getFileName(), hex);
            return hex;
        } catch (NoSuchAlgorithmException e) {
            throw new FileProcessingException("UNSUPPORTED_ALGORITHM", "Algorithm not available: " + algorithm, e);
        } catch (IOException e) {
            throw new FileProcessingException("CHECKSUM_READ_ERROR", "Failed to read file for checksum: " + filePath, e);
        }
    }

    /**
     * Verifies that a local file's calculated checksum matches the expected checksum.
     */
    public ChecksumResult verifyChecksum(Path filePath, String expectedChecksum, ChecksumAlgorithm algorithm) {
        String calculated = calculateChecksum(filePath, algorithm);
        ChecksumResult result = ChecksumResult.verified(expectedChecksum, calculated, algorithm);

        if (!result.isMatches()) {
            log.error("Integrity verification failed for [{}]. Expected: [{}], Calculated: [{}]",
                    filePath.getFileName(), expectedChecksum, calculated);
            throw new IntegrityException(expectedChecksum, calculated, filePath.toString());
        }

        log.info("Integrity verification succeeded for [{}] ({}): {}",
                filePath.getFileName(), algorithm, calculated);
        return result;
    }

    /**
     * Parses a companion checksum file (.sha256).
     * Supports formats:
     * - "<checksum> *filename" (standard sha256sum binary format)
     * - "<checksum>  filename" (standard sha256sum text format)
     * - "<checksum>" (bare digest format)
     */
    public String parseCompanionChecksumFile(Path companionFilePath) {
        if (companionFilePath == null || !Files.exists(companionFilePath)) {
            throw new ValidationException("COMPANION_FILE_NOT_FOUND", "Companion checksum file not found: " + companionFilePath);
        }

        try {
            String content = Files.readString(companionFilePath).trim();
            if (content.isBlank()) {
                throw new IntegrityException("Companion checksum file is empty: " + companionFilePath);
            }

            // Split on whitespace to extract first token
            String[] tokens = content.split("\\s+");
            String checksumToken = tokens[0].trim();

            // Validate that token is valid hex string (64 characters for SHA-256, 32 for MD5)
            if (!checksumToken.matches("^[a-fA-F0-9]{32,128}$")) {
                throw new IntegrityException("Malformed checksum token in companion file: " + checksumToken);
            }

            return checksumToken;
        } catch (IOException e) {
            throw new FileProcessingException("COMPANION_READ_ERROR", "Failed to read companion file: " + companionFilePath, e);
        }
    }

    /**
     * Creates a companion checksum file next to the target file.
     * Generates standard GNU sha256sum format: "<checksum>  <filename>"
     */
    public Path writeCompanionChecksumFile(Path targetFile, String checksum, ChecksumAlgorithm algorithm) {
        String companionFileName = targetFile.getFileName().toString() + "." + algorithm.name().toLowerCase().replace("_", "");
        Path companionPath = targetFile.resolveSibling(companionFileName);

        String line = String.format("%s  %s%n", checksum, targetFile.getFileName().toString());
        try {
            Files.writeString(companionPath, line);
            log.info("Generated checksum companion file: [{}]", companionPath);
            return companionPath;
        } catch (IOException e) {
            throw new FileProcessingException("COMPANION_WRITE_ERROR", "Failed to write companion checksum file: " + companionPath, e);
        }
    }
}
