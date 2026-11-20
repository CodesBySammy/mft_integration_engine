package com.mft.integration.orchestrator;

import com.mft.integration.adapter.read.ReadAdapter;
import com.mft.integration.adapter.write.WriteAdapter;
import com.mft.integration.status.TransferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class IdempotencyAndRetryLiveIntegrationTest {

    @Autowired
    private RouteOrchestrator routeOrchestrator;

    @Autowired
    private RouteRegistry routeRegistry;

    @Autowired
    private ReadAdapter readAdapter;

    @Autowired
    private WriteAdapter writeAdapter;

    @Test
    @DisplayName("Live End-to-End: Idempotency detection skips duplicate files and honors allowReprocessing")
    void testLiveIdempotencyLifecycle() throws IOException {
        String testFilename = "idempotency_test_" + System.currentTimeMillis() + ".txt";
        Path sourceServerDir = Path.of("D:\\sftpgo\\instance-a\\data\\mft_source\\upload");
        Files.createDirectories(sourceServerDir);
        Path sourceServerFile = sourceServerDir.resolve(testFilename);
        Files.writeString(sourceServerFile, "IDEMPOTENT_TRANSACTION_PAYLOAD_1001\n");

        try {
            // Verify file is visible via ReadAdapter on Server A (port 2022)
            boolean existsOnSource = readAdapter.fileExists("C_FILE_IN", "/upload/" + testFilename);
            assertThat(existsOnSource).isTrue();

            // Step 1: First execution -> Must successfully discover and transfer the file
            RouteExecutionResult firstRunResult = routeOrchestrator.executeRoute("R_RAW_COPY", testFilename);

            assertThat(firstRunResult.isSuccess()).isTrue();
            assertThat(firstRunResult.getFilesDiscovered()).isEqualTo(1);
            assertThat(firstRunResult.getFilesTransferred()).isEqualTo(1);
            assertThat(firstRunResult.getFilesSkipped()).isEqualTo(0);
            assertThat(firstRunResult.getItemRuns().get(0).getStatus()).isEqualTo(TransferStatus.COMPLETED);

            // Verify publication on Destination SFTP (Server B on port 2222)
            boolean existsOnDest = writeAdapter.remoteFileExists("C_FILE_OUT", "/incoming/" + testFilename);
            assertThat(existsOnDest).isTrue();

            // Step 2: Set allowReprocessing=false -> Second execution with identical file must be detected as duplicate and SKIPPED
            RouteDefinition route = routeRegistry.getRoute("R_RAW_COPY");
            route.setAllowReprocessing(false);
            RouteExecutionResult secondRunResult = routeOrchestrator.executeRoute("R_RAW_COPY", testFilename);

            assertThat(secondRunResult.isSuccess()).isTrue();
            assertThat(secondRunResult.getFilesDiscovered()).isEqualTo(1);
            assertThat(secondRunResult.getFilesTransferred()).isEqualTo(0);
            assertThat(secondRunResult.getFilesSkipped()).isEqualTo(1);
            assertThat(secondRunResult.getItemRuns().get(0).getStatus()).isEqualTo(TransferStatus.SKIPPED_DUPLICATE);

            // Step 3: Enable allowReprocessing on the route -> Third execution must re-transfer
            route.setAllowReprocessing(true);
            try {
                RouteExecutionResult thirdRunResult = routeOrchestrator.executeRoute("R_RAW_COPY", testFilename);

                assertThat(thirdRunResult.isSuccess()).isTrue();
                assertThat(thirdRunResult.getFilesDiscovered()).isEqualTo(1);
                assertThat(thirdRunResult.getFilesTransferred()).isEqualTo(1);
                assertThat(thirdRunResult.getFilesSkipped()).isEqualTo(0);
                assertThat(thirdRunResult.getItemRuns().get(0).getStatus()).isEqualTo(TransferStatus.COMPLETED);
            } finally {
                // Restore default
                route.setAllowReprocessing(true);
            }
        } finally {
            Files.deleteIfExists(sourceServerFile);
        }
    }
}
