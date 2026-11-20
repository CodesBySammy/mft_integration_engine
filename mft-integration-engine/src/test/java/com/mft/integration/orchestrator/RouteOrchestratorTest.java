package com.mft.integration.orchestrator;

import com.mft.integration.adapter.model.RemoteFileInfo;
import com.mft.integration.adapter.model.TransferResult;
import com.mft.integration.adapter.read.ReadAdapter;
import com.mft.integration.adapter.write.WriteAdapter;
import com.mft.integration.audit.AuditService;
import com.mft.integration.audit.InMemoryAuditRepository;
import com.mft.integration.checksum.ChecksumAlgorithm;
import com.mft.integration.checksum.ChecksumService;
import com.mft.integration.idempotency.IdempotencyService;
import com.mft.integration.idempotency.InMemoryIdempotencyRepository;
import com.mft.integration.processing.RawCopyProcessor;
import com.mft.integration.processing.TransformationProcessor;
import com.mft.integration.retry.RetryManager;
import com.mft.integration.status.InMemoryTransferStatusRepository;
import com.mft.integration.status.TransferStatus;
import com.mft.integration.status.TransferStatusService;
import com.mft.integration.workspace.LocalWorkspaceService;
import com.mft.integration.workspace.WorkspaceDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteOrchestratorTest {

    private RouteRegistry routeRegistry;
    private ReadAdapter readAdapter;
    private WriteAdapter writeAdapter;
    private RawCopyProcessor rawCopyProcessor;
    private TransformationProcessor transformationProcessor;
    private LocalWorkspaceService workspaceService;
    private ChecksumService checksumService;
    private IdempotencyService idempotencyService;
    private RetryManager retryManager;
    private TransferStatusService statusService;
    private AuditService auditService;
    private RouteOrchestrator orchestrator;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        routeRegistry = Mockito.mock(RouteRegistry.class);
        readAdapter = Mockito.mock(ReadAdapter.class);
        writeAdapter = Mockito.mock(WriteAdapter.class);
        rawCopyProcessor = Mockito.mock(RawCopyProcessor.class);
        transformationProcessor = Mockito.mock(TransformationProcessor.class);
        workspaceService = new LocalWorkspaceService(tempDir.toString());
        workspaceService.initialize();
        checksumService = new ChecksumService();
        idempotencyService = new IdempotencyService(new InMemoryIdempotencyRepository());
        auditService = new AuditService(new InMemoryAuditRepository());
        retryManager = new RetryManager(auditService);
        statusService = new TransferStatusService(new InMemoryTransferStatusRepository());

        orchestrator = new RouteOrchestrator(
                routeRegistry,
                readAdapter,
                writeAdapter,
                rawCopyProcessor,
                transformationProcessor,
                workspaceService,
                checksumService,
                idempotencyService,
                retryManager,
                statusService,
                auditService
        );
    }

    @Test
    @DisplayName("Orchestrates raw copy route from discovery to atomic publication")
    void testRawCopyExecutionFlow() throws IOException {
        RouteDefinition route = new RouteDefinition(
                "R_RAW_COPY",
                "C_FILE_IN",
                "C_FILE_OUT",
                ProcessingMode.RAW_COPY,
                "/upload",
                "*.txt",
                "/incoming"
        );
        when(routeRegistry.getRoute("R_RAW_COPY")).thenReturn(route);

        RemoteFileInfo remoteFile = new RemoteFileInfo(
                "C_FILE_IN", "/upload/trades.txt", "trades.txt", 100L, Instant.now(), false, "rw"
        );
        when(readAdapter.discoverFiles("C_FILE_IN", "/upload", "*.txt"))
                .thenReturn(List.of(remoteFile));

        Path downloaded = workspaceService.resolvePath(WorkspaceDirectory.DOWNLOADS, "trades.txt");
        Files.writeString(downloaded, "TRADE DATA");
        when(readAdapter.downloadFile(eq("C_FILE_IN"), eq("/upload/trades.txt"), any(Path.class), anyBoolean()))
                .thenReturn(downloaded);

        Path processedOutput = workspaceService.resolvePath(WorkspaceDirectory.OUTPUT, "trades.txt");
        Files.writeString(processedOutput, "TRADE DATA");
        when(rawCopyProcessor.process(any(Path.class), any(ChecksumAlgorithm.class)))
                .thenReturn(new RawCopyProcessor.RawCopyResult(processedOutput, "abcd1234checksum"));

        when(writeAdapter.publishFile(eq("C_FILE_OUT"), any(Path.class), eq("/incoming"), eq("trades.txt"), anyBoolean()))
                .thenReturn(TransferResult.success("C_FILE_OUT", "/incoming", "trades.txt", "/incoming/trades.txt", 10L));

        RouteExecutionResult result = orchestrator.executeRoute("R_RAW_COPY");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFilesDiscovered()).isEqualTo(1);
        assertThat(result.getFilesTransferred()).isEqualTo(1);
        assertThat(result.getFilesSkipped()).isEqualTo(0);
        assertThat(result.getFilesFailed()).isEqualTo(0);

        assertThat(result.getItemRuns()).hasSize(1);
        assertThat(result.getItemRuns().get(0).getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.getItemRuns().get(0).getOutputChecksum()).isNotBlank();

        verify(writeAdapter).publishFile(eq("C_FILE_OUT"), any(Path.class), eq("/incoming"), eq("trades.txt"), eq(true));
        assertThat(auditService.getAllEvents()).isNotEmpty();
    }
}
