package com.mft.integration.orchestrator;

import com.mft.integration.adapter.read.ReadAdapter;
import com.mft.integration.adapter.write.WriteAdapter;
import com.mft.integration.checksum.ChecksumAlgorithm;
import com.mft.integration.checksum.ChecksumService;
import com.mft.integration.idempotency.IdempotencyRepository;
import com.mft.integration.status.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@org.springframework.test.context.TestPropertySource(properties = "mft.scheduling.enabled=false")
class RawCopyLiveRouteIntegrationTest {

    @Autowired
    private RouteOrchestrator routeOrchestrator;

    @Autowired
    private ReadAdapter readAdapter;

    @Autowired
    private WriteAdapter writeAdapter;

    @Autowired
    private ChecksumService checksumService;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @BeforeEach
    void setUp() {
        idempotencyRepository.clear();
    }

    @Test
    @DisplayName("Live End-to-End: Execute R_RAW_COPY transferring positions.txt from Server A to Server B")
    void testLiveRawCopyRoute(@TempDir Path tempDir) throws IOException {
        // Step 1: Execute the configured raw-copy route
        RouteExecutionResult result = routeOrchestrator.executeRoute("R_RAW_COPY");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFilesDiscovered()).isGreaterThanOrEqualTo(1);
        assertThat(result.getFilesTransferred()).isGreaterThanOrEqualTo(1);
        assertThat(result.getFilesFailed()).isEqualTo(0);

        // Step 2: Verify that positions.txt was published on Destination SFTP (Server B on port 2222)
        boolean existsOnDestination = writeAdapter.remoteFileExists("C_FILE_OUT", "/incoming/positions.txt");
        assertThat(existsOnDestination).isTrue();

        // Step 3: Download source and destination files to verify exact cryptographic SHA-256 match
        Path sourceDownloaded = tempDir.resolve("source_positions.txt");
        Path destinationDownloaded = tempDir.resolve("dest_positions.txt");

        readAdapter.downloadFile("C_FILE_IN", "/upload/positions.txt", sourceDownloaded, true);
        readAdapter.downloadFile("C_FILE_OUT", "/incoming/positions.txt", destinationDownloaded, true);

        String sourceChecksum = checksumService.calculateChecksum(sourceDownloaded, ChecksumAlgorithm.SHA_256);
        String destinationChecksum = checksumService.calculateChecksum(destinationDownloaded, ChecksumAlgorithm.SHA_256);

        assertThat(destinationChecksum).isEqualTo(sourceChecksum);
        assertThat(Files.readAllBytes(destinationDownloaded)).containsExactly(Files.readAllBytes(sourceDownloaded));

        // Step 4: Verify transfer run status
        assertThat(result.getItemRuns()).isNotEmpty();
        assertThat(result.getItemRuns().get(0).getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }
}
