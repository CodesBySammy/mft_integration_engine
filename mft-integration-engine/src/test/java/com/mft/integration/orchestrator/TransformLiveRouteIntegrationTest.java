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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class TransformLiveRouteIntegrationTest {

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
    @DisplayName("Live End-to-End: Execute R_TRANSFORM (Fixed-Width -> Delimited) publishing payload, companion and manifest to Server B")
    void testLiveTransformRoute(@TempDir Path tempDir) throws IOException {
        // Step 1: Execute R_TRANSFORM
        RouteExecutionResult result = routeOrchestrator.executeRoute("R_TRANSFORM");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFilesDiscovered()).isGreaterThanOrEqualTo(1);
        assertThat(result.getFilesTransferred()).isGreaterThanOrEqualTo(1);
        assertThat(result.getFilesFailed()).isEqualTo(0);

        String publishedFilename = result.getItemRuns().get(0).getDestinationFilename();
        assertThat(publishedFilename).startsWith("SL_RECON_");
        assertThat(publishedFilename).endsWith(".txt");

        // Step 2: Verify presence of data payload, companion, and manifest on Destination SFTP (Server B)
        boolean dataExists = writeAdapter.remoteFileExists("C_FILE_OUT", "/incoming/" + publishedFilename);
        boolean companionExists = writeAdapter.remoteFileExists("C_FILE_OUT", "/incoming/" + publishedFilename + ".sha256");
        boolean manifestExists = writeAdapter.remoteFileExists("C_FILE_OUT", "/incoming/" + publishedFilename + ".manifest.json");

        assertThat(dataExists).isTrue();
        assertThat(companionExists).isTrue();
        assertThat(manifestExists).isTrue();

        // Step 3: Download and verify the generated delimited output from Server B
        Path downloadedData = tempDir.resolve("downloaded_" + publishedFilename);
        readAdapter.downloadFile("C_FILE_OUT", "/incoming/" + publishedFilename, downloadedData, true);

        List<String> lines = Files.readAllLines(downloadedData);
        assertThat(lines.size()).isGreaterThanOrEqualTo(6); // Header + 4 detail rows + Trailer
        assertThat(lines.get(0)).startsWith("HDR|20261120|R_TRANSFORM|");
        assertThat(lines.get(1)).startsWith("REC000000001|TRD000000001|USD|1500.50|20261120|PROCESSED");
        assertThat(lines.get(lines.size() - 1)).isEqualTo("TRL|4|60251.25");

        // Step 4: Verify companion checksum matches downloaded data hash
        Path downloadedCompanion = tempDir.resolve("downloaded_companion.sha256");
        readAdapter.downloadFile("C_FILE_OUT", "/incoming/" + publishedFilename + ".sha256", downloadedCompanion, true);
        String companionChecksum = checksumService.parseCompanionChecksumFile(downloadedCompanion);
        String actualDataChecksum = checksumService.calculateChecksum(downloadedData, ChecksumAlgorithm.SHA_256);
        assertThat(companionChecksum).isEqualTo(actualDataChecksum);

        // Step 5: Verify JSON manifest content
        Path downloadedManifest = tempDir.resolve("downloaded_manifest.json");
        readAdapter.downloadFile("C_FILE_OUT", "/incoming/" + publishedFilename + ".manifest.json", downloadedManifest, true);
        String manifestContent = Files.readString(downloadedManifest);
        assertThat(manifestContent).contains("\"interfaceId\" : \"MFT-RECON-INT\"");
        assertThat(manifestContent).contains("\"recordCount\" : 4");
        assertThat(manifestContent).contains(actualDataChecksum);

        // Step 6: Verify TransferRun lifecycle
        assertThat(result.getItemRuns().get(0).getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.getItemRuns().get(0).getRecordCount()).isEqualTo(4);
    }
}
