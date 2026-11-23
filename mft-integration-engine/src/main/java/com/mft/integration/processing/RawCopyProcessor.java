package com.mft.integration.processing;

import com.mft.integration.checksum.ChecksumAlgorithm;
import com.mft.integration.checksum.ChecksumService;
import com.mft.integration.exception.FileProcessingException;
import com.mft.integration.exception.IntegrityException;
import com.mft.integration.workspace.LocalWorkspaceService;
import com.mft.integration.workspace.WorkspaceDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Processor for RAW_COPY routes.
 * Preserves exact original bytes without decoding, charset conversion, or re-encoding.
 */
@Service
public class RawCopyProcessor {

    private static final Logger log = LoggerFactory.getLogger(RawCopyProcessor.class);

    private final ChecksumService checksumService;
    private final LocalWorkspaceService workspaceService;

    public RawCopyProcessor(ChecksumService checksumService, LocalWorkspaceService workspaceService) {
        this.checksumService = checksumService;
        this.workspaceService = workspaceService;
    }

    /**
     * Executes raw copy:
     * 1. Calculates input checksum.
     * 2. Copies raw bytes to workspace/output.
     * 3. Calculates output checksum and confirms exact match.
     *
     * @param downloadedFile source file in downloads directory
     * @param algorithm checksum algorithm to verify
     * @return RawCopyResult containing output file and verified checksum
     */
    public RawCopyResult process(Path downloadedFile, ChecksumAlgorithm algorithm) {
        return process(downloadedFile, algorithm, null);
    }

    public RawCopyResult process(Path downloadedFile, ChecksumAlgorithm algorithm, String runId) {
        String filename = downloadedFile.getFileName().toString();
        log.info("Processing RAW_COPY for [{}] in run [{}]", filename, runId);

        // 1. Calculate input checksum
        String inputChecksum = checksumService.calculateChecksum(downloadedFile, algorithm);

        // 2. Stage raw copy to output directory with run-level isolation
        Path outputFile = workspaceService.resolvePath(WorkspaceDirectory.OUTPUT, runId, filename);
        try {
            Files.copy(downloadedFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied raw bytes: [{}] -> [{}] ({} bytes)",
                    downloadedFile.getFileName(), outputFile.getFileName(), Files.size(outputFile));
        } catch (IOException e) {
            throw new FileProcessingException("RAW_COPY_IO_ERROR", "Failed to copy raw file to output: " + filename, e);
        }

        // 3. Calculate output checksum
        String outputChecksum = checksumService.calculateChecksum(outputFile, algorithm);

        // 4. Verify identical checksums
        if (!inputChecksum.equalsIgnoreCase(outputChecksum)) {
            throw new IntegrityException(inputChecksum, outputChecksum, outputFile.toString());
        }

        log.info("RAW_COPY completed with matching {} checksum: {}", algorithm, outputChecksum);
        return new RawCopyResult(outputFile, outputChecksum);
    }

    public record RawCopyResult(Path outputFile, String checksum) {
    }
}
