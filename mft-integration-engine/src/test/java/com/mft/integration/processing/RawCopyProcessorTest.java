package com.mft.integration.processing;

import com.mft.integration.checksum.ChecksumAlgorithm;
import com.mft.integration.checksum.ChecksumService;
import com.mft.integration.workspace.LocalWorkspaceService;
import com.mft.integration.workspace.WorkspaceDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RawCopyProcessorTest {

    private RawCopyProcessor processor;
    private ChecksumService checksumService;
    private LocalWorkspaceService workspaceService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        checksumService = new ChecksumService();
        workspaceService = new LocalWorkspaceService(tempDir.toString());
        workspaceService.initialize();
        processor = new RawCopyProcessor(checksumService, workspaceService);
    }

    @Test
    @DisplayName("Copies original bytes without alteration and verifies matching checksum")
    void testRawCopyExactBytes(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("original_trade.dat");
        byte[] payload = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, 'A', 'B', 'C'};
        Files.write(sourceFile, payload);

        RawCopyProcessor.RawCopyResult result = processor.process(sourceFile, ChecksumAlgorithm.SHA_256);

        assertThat(Files.exists(result.outputFile())).isTrue();
        assertThat(Files.readAllBytes(result.outputFile())).containsExactly(payload);
        assertThat(result.checksum()).isEqualTo(checksumService.calculateChecksum(sourceFile, ChecksumAlgorithm.SHA_256));
    }
}
