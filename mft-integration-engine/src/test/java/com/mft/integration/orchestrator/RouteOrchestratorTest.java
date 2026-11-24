package com.mft.integration.orchestrator;

import com.mft.integration.adapter.model.RemoteFileInfo;
import com.mft.integration.adapter.model.TransferResult;
import com.mft.integration.adapter.read.ReadAdapter;
import com.mft.integration.adapter.write.WriteAdapter;
import com.mft.integration.audit.AuditService;
import com.mft.integration.audit.InMemoryAuditRepository;
import com.mft.integration.checksum.ChecksumAlgorithm;
import com.mft.integration.checksum.ChecksumService;
import com.mft.integration.compression.FileCompressionService;
import com.mft.integration.idempotency.IdempotencyService;
import com.mft.integration.idempotency.InMemoryIdempotencyRepository;
import com.mft.integration.processing.RawCopyProcessor;
import com.mft.integration.processing.TransformationProcessor;
import com.mft.integration.retry.RetryManager;
import com.mft.integration.status.InMemoryTransferStatusRepository;
import com.mft.integration.status.TransferRun;
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
    private FileCompressionService fileCompressionService;
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
        fileCompressionService = new FileCompressionService(workspaceService, checksumService);
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
                fileCompressionService,
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
        Files.writeString(downloaded, "TRADE DATA - ".repeat(100)); // > 1 KB (1300 bytes)
        when(readAdapter.downloadFile(eq("C_FILE_IN"), eq("/upload/trades.txt"), any(Path.class), anyBoolean()))
                .thenReturn(downloaded);

        Path processedOutput = workspaceService.resolvePath(WorkspaceDirectory.OUTPUT, "trades.txt");
        Files.writeString(processedOutput, "TRADE DATA - ".repeat(100));
        when(rawCopyProcessor.process(any(Path.class), any(ChecksumAlgorithm.class)))
                .thenReturn(new RawCopyProcessor.RawCopyResult(processedOutput, "abcd1234checksum"));

        when(writeAdapter.publishFile(eq("C_FILE_OUT"), any(Path.class), eq("/incoming"), eq("trades.txt"), anyBoolean()))
                .thenReturn(TransferResult.success("C_FILE_OUT", "/incoming", "trades.txt", "/incoming/trades.txt", 1300L));

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
        // Verify source files are NEVER deleted (preserves original files at source)
        verify(readAdapter, org.mockito.Mockito.never()).deleteFile(any(), any());
        assertThat(auditService.getAllEvents()).isNotEmpty();
    }

    @Test
    @DisplayName("Rejects undersized files (< 1 KB) and logs failure audit")
    void testUndersizedFileRejection() throws IOException {
        RouteDefinition route = new RouteDefinition(
                "R_RAW_COPY",
                "C_FILE_IN",
                "C_FILE_OUT",
                ProcessingMode.RAW_COPY,
                "/upload",
                "*.txt",
                "/incoming"
        );
        route.setMinFileSize(1024L);
        when(routeRegistry.getRoute("R_RAW_COPY")).thenReturn(route);

        RemoteFileInfo remoteFile = new RemoteFileInfo(
                "C_FILE_IN", "/upload/tiny.txt", "tiny.txt", 50L, Instant.now(), false, "rw"
        );
        when(readAdapter.discoverFiles("C_FILE_IN", "/upload", "*.txt"))
                .thenReturn(List.of(remoteFile));

        Path downloaded = workspaceService.resolvePath(WorkspaceDirectory.DOWNLOADS, "tiny.txt");
        Files.writeString(downloaded, "tiny 50-byte content"); // 21 bytes < 1024 bytes
        when(readAdapter.downloadFile(eq("C_FILE_IN"), eq("/upload/tiny.txt"), any(Path.class), anyBoolean()))
                .thenReturn(downloaded);

        RouteExecutionResult result = orchestrator.executeRoute("R_RAW_COPY");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFilesDiscovered()).isEqualTo(1);
        assertThat(result.getFilesTransferred()).isEqualTo(0);
        assertThat(result.getFilesFailed()).isEqualTo(1);

        assertThat(result.getItemRuns()).hasSize(1);
        TransferRun run = result.getItemRuns().get(0);
        assertThat(run.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(run.getErrorCode()).isEqualTo("FILE_UNDERSIZED");
        assertThat(run.getErrorMessage()).contains("below minimum allowed threshold");
    }

    @Test
    @DisplayName("Automatically compresses large files exceeding 10 MB threshold to GZIP")
    void testLargeFileAutomaticCompression() throws IOException {
        RouteDefinition route = new RouteDefinition(
                "R_RAW_COPY",
                "C_FILE_IN",
                "C_FILE_OUT",
                ProcessingMode.RAW_COPY,
                "/upload",
                "*.txt",
                "/incoming"
        );
        // Set threshold to 2000 bytes for deterministic unit test execution
        route.setMaxStandardFileSize(2000L);
        route.setCompressLargeFiles(true);
        when(routeRegistry.getRoute("R_RAW_COPY")).thenReturn(route);

        RemoteFileInfo remoteFile = new RemoteFileInfo(
                "C_FILE_IN", "/upload/large_report.txt", "large_report.txt", 5000L, Instant.now(), false, "rw"
        );
        when(readAdapter.discoverFiles("C_FILE_IN", "/upload", "*.txt"))
                .thenReturn(List.of(remoteFile));

        Path downloaded = workspaceService.resolvePath(WorkspaceDirectory.DOWNLOADS, "large_report.txt");
        Files.writeString(downloaded, "DATA RECORD PADDING ".repeat(250)); // 5000 bytes > 2000 bytes
        when(readAdapter.downloadFile(eq("C_FILE_IN"), eq("/upload/large_report.txt"), any(Path.class), anyBoolean()))
                .thenReturn(downloaded);

        when(writeAdapter.publishFile(eq("C_FILE_OUT"), any(Path.class), eq("/incoming"), eq("large_report.txt.gz"), anyBoolean()))
                .thenReturn(TransferResult.success("C_FILE_OUT", "/incoming", "large_report.txt.gz", "/incoming/large_report.txt.gz", 250L));

        RouteExecutionResult result = orchestrator.executeRoute("R_RAW_COPY");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFilesDiscovered()).isEqualTo(1);
        assertThat(result.getFilesTransferred()).isEqualTo(1);

        assertThat(result.getItemRuns()).hasSize(1);
        TransferRun run = result.getItemRuns().get(0);
        assertThat(run.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(run.getDestinationFilename()).isEqualTo("large_report.txt.gz");

        verify(writeAdapter).publishFile(eq("C_FILE_OUT"), any(Path.class), eq("/incoming"), eq("large_report.txt.gz"), eq(true));
        assertThat(auditService.getAllEvents().stream().anyMatch(e -> "FILE_COMPRESSED".equals(e.getOperation()))).isTrue();
    }
}
