package com.mft.integration.orchestrator;

import com.mft.integration.adapter.model.RemoteFileInfo;
import com.mft.integration.adapter.model.TransferResult;
import com.mft.integration.adapter.read.ReadAdapter;
import com.mft.integration.adapter.write.WriteAdapter;
import com.mft.integration.audit.AuditService;
import com.mft.integration.checksum.ChecksumService;
import com.mft.integration.compression.FileCompressionService;
import com.mft.integration.exception.ConcurrencyLimitExceededException;
import com.mft.integration.exception.FileProcessingException;
import com.mft.integration.exception.MftException;
import com.mft.integration.exception.RouteExecutionLockedException;
import com.mft.integration.idempotency.IdempotencyKey;
import com.mft.integration.idempotency.IdempotencyService;
import com.mft.integration.processing.RawCopyProcessor;
import com.mft.integration.processing.TransformationProcessor;
import com.mft.integration.retry.RetryManager;
import com.mft.integration.retry.RetryPolicy;
import com.mft.integration.status.TransferRun;
import com.mft.integration.status.TransferStatus;
import com.mft.integration.status.TransferStatusService;
import com.mft.integration.workspace.LocalWorkspaceService;
import com.mft.integration.workspace.WorkspaceDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main route orchestration engine.
 * Coordinates ReadAdapter, WriteAdapter, Processing (RAW_COPY, TRANSFORM),
 * Idempotency checking, Retry policies, Status tracking, and Audit logging.
 */
@Service
public class RouteOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RouteOrchestrator.class);

    private final RouteRegistry routeRegistry;
    private final ReadAdapter readAdapter;
    private final WriteAdapter writeAdapter;
    private final RawCopyProcessor rawCopyProcessor;
    private final TransformationProcessor transformationProcessor;
    private final FileCompressionService fileCompressionService;
    private final LocalWorkspaceService workspaceService;
    private final ChecksumService checksumService;
    private final IdempotencyService idempotencyService;
    private final RetryManager retryManager;
    private final TransferStatusService statusService;
    private final AuditService auditService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.mft.integration.connection.ConnectionResolver connectionResolver;

    private final Map<String, Instant> duplicateAuditThrottle = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.locks.ReentrantLock> routeLocks = new ConcurrentHashMap<>();
    private final java.util.concurrent.Semaphore globalTransferLimiter = new java.util.concurrent.Semaphore(10);

    public RouteOrchestrator(RouteRegistry routeRegistry,
                             ReadAdapter readAdapter,
                             WriteAdapter writeAdapter,
                             RawCopyProcessor rawCopyProcessor,
                             TransformationProcessor transformationProcessor,
                             FileCompressionService fileCompressionService,
                             LocalWorkspaceService workspaceService,
                             ChecksumService checksumService,
                             IdempotencyService idempotencyService,
                             RetryManager retryManager,
                             TransferStatusService statusService,
                             AuditService auditService) {
        this.routeRegistry = routeRegistry;
        this.readAdapter = readAdapter;
        this.writeAdapter = writeAdapter;
        this.rawCopyProcessor = rawCopyProcessor;
        this.transformationProcessor = transformationProcessor;
        this.fileCompressionService = fileCompressionService;
        this.workspaceService = workspaceService;
        this.checksumService = checksumService;
        this.idempotencyService = idempotencyService;
        this.retryManager = retryManager;
        this.statusService = statusService;
        this.auditService = auditService;
    }

    public RouteExecutionResult executeRoute(String routeId) {
        return executeRoute(routeId, null);
    }

    public RouteExecutionResult executeRoute(String routeId, String patternOverride) {
        RouteDefinition route = routeRegistry.getRoute(routeId);

        // Guard 1: Route-level execution lock to prevent race conditions & duplicate file collisions
        java.util.concurrent.locks.ReentrantLock routeLock = routeLocks.computeIfAbsent(routeId, k -> new java.util.concurrent.locks.ReentrantLock());
        if (!routeLock.tryLock()) {
            log.warn("Concurrent execution rejected: Route [{}] is already actively executing.", routeId);
            throw new RouteExecutionLockedException(routeId,
                    "Route [" + routeId + "] is currently executing. Concurrent execution prevented to eliminate race conditions.");
        }

        // Guard 2: Global engine concurrency limiter (bulkhead pattern)
        if (!globalTransferLimiter.tryAcquire()) {
            routeLock.unlock();
            log.warn("Global transfer concurrency limit reached (max 10 active transfers). Route [{}] postponed.", routeId);
            throw new ConcurrencyLimitExceededException(
                    "Engine concurrency limit reached. Maximum concurrent route transfers are active. Please retry shortly.");
        }

        try {
            return doExecuteRoute(route, patternOverride);
        } finally {
            globalTransferLimiter.release();
            routeLock.unlock();
        }
    }

    private RouteExecutionResult doExecuteRoute(RouteDefinition route, String patternOverride) {
        String routeId = route.getRouteId();
        String runId = generateRunId(routeId);
        Instant startTime = Instant.now();

        RetryPolicy routeRetryPolicy = new RetryPolicy(
                route.getRetryLimit(),
                route.getRetryInitialDelayMs(),
                route.getRetryMultiplier(),
                10000L,
                0.1
        );

        String address = resolveAddress(route);

        log.info("Starting execution: address=[{}], runId=[{}], mode=[{}], allowReprocessing=[{}]",
                address, runId, route.getProcessingMode(), route.isAllowReprocessing());
        auditService.recordEvent(runId, routeId, address,
                "ROUTE_START", null, null, "STARTED", null,
                "Sync initiated: " + address + " [ID: " + runId + "]");

        String effectivePattern = (patternOverride != null && !patternOverride.isBlank())
                ? patternOverride
                : route.getSourcePattern();

        // Step 1: File Discovery on Source SFTP (with retry)
        List<RemoteFileInfo> discoveredFiles;
        try {
            discoveredFiles = retryManager.execute(
                    "DISCOVERY",
                    runId,
                    routeId,
                    routeRetryPolicy,
                    () -> readAdapter.discoverFiles(
                            route.getSourceConnectionId(),
                            route.getSourceDirectory(),
                            effectivePattern
                    )
            );
        } catch (Exception e) {
            log.error("File discovery failed for route [{}] on [{}]: {}", routeId, route.getSourceConnectionId(), e.getMessage());
            auditService.recordEvent(runId, routeId, route.getSourceConnectionId(), route.getDestinationConnectionId(),
                    "DISCOVERY_FAILED", null, null, "FAILED", "DISCOVERY_ERROR", e.getMessage());
            return new RouteExecutionResult(runId, routeId, false, 0, 0, 0, 0, startTime, Instant.now(),
                    "Discovery failed: " + e.getMessage(), List.of());
        }

        if (discoveredFiles.isEmpty()) {
            log.info("No files matching pattern [{}] discovered on [{}:{}]",
                    effectivePattern, route.getSourceConnectionId(), route.getSourceDirectory());
            return new RouteExecutionResult(runId, routeId, true, 0, 0, 0, 0, startTime, Instant.now(),
                    "No files found matching pattern: " + effectivePattern, List.of());
        }

        List<TransferRun> completedRuns = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        // Step 2: Process each discovered file
        for (RemoteFileInfo remoteFile : discoveredFiles) {
            String itemRunId = runId + "-" + remoteFile.getFilename();
            TransferRun transferRun = new TransferRun(
                    itemRunId,
                    routeId,
                    route.getSourceConnectionId(),
                    route.getDestinationConnectionId(),
                    remoteFile.getFilename()
            );
            transferRun.setDestinationFilename(remoteFile.getFilename());

            try {
                boolean transferred = processSingleFile(route, routeRetryPolicy, transferRun, remoteFile);
                if (transferred) {
                    successCount++;
                    statusService.registerRun(transferRun);
                    completedRuns.add(transferRun);
                } else {
                    skippedCount++;
                    completedRuns.add(transferRun);
                    // Register skip record in statusService with stable key to show on dashboard without storage bloat
                    TransferRun skipRecord = new TransferRun(
                            "SKIP_" + routeId + "_" + remoteFile.getFilename(),
                            routeId,
                            route.getSourceConnectionId(),
                            route.getDestinationConnectionId(),
                            remoteFile.getFilename()
                    );
                    skipRecord.setStatus(TransferStatus.SKIPPED_DUPLICATE);
                    skipRecord.setSourceChecksum(transferRun.getSourceChecksum());
                    skipRecord.setOutputChecksum(transferRun.getSourceChecksum());
                    skipRecord.setBytesTransferred(transferRun.getBytesTransferred());
                    skipRecord.setEndTime(Instant.now());
                    statusService.registerRun(skipRecord);

                    // Throttle duplicate audit events so we don't spam disk on every 5s polling cycle
                    String auditThrottleKey = routeId + ":" + remoteFile.getFilename();
                    Instant lastAudit = duplicateAuditThrottle.get(auditThrottleKey);
                    if (lastAudit == null || lastAudit.isBefore(Instant.now().minusSeconds(60))) {
                        duplicateAuditThrottle.put(auditThrottleKey, Instant.now());
                        auditService.recordEvent(
                                itemRunId,
                                routeId,
                                address,
                                "SKIPPED_DUPLICATE",
                                remoteFile.getFilename(),
                                null,
                                "SKIPPED",
                                null,
                                "Duplicate file detected [" + remoteFile.getFilename() + "] (checksum matches previously transferred file). Zero re-transfer guarantee preserved."
                        );
                    }
                }
            } catch (Exception e) {
                log.error("Failed transfer of file [{}] in run [{}]: {}", remoteFile.getFilename(), runId, e.getMessage());
                failureCount++;
                transferRun.setStatus(TransferStatus.FAILED);
                transferRun.setEndTime(Instant.now());
                String errorCode = (e instanceof MftException me) ? me.getErrorCode() : "TRANSFER_FAILED";
                transferRun.setErrorCode(errorCode);
                transferRun.setErrorMessage(e.getMessage());
                statusService.registerRun(transferRun);
                auditService.recordEvent(itemRunId, routeId, route.getSourceConnectionId(), route.getDestinationConnectionId(),
                        "FILE_TRANSFER_FAILED", remoteFile.getFilename(), null, "FAILED", errorCode, e.getMessage());
                completedRuns.add(transferRun);
            }
        }

        Instant endTime = Instant.now();
        boolean overallSuccess = failureCount == 0;
        String summary = String.format("Completed sync [%s]. Discovered: %d, Transferred: %d, Skipped: %d, Failed: %d [ID: %s]",
                address, discoveredFiles.size(), successCount, skippedCount, failureCount, runId);

        log.info(summary);
        auditService.recordEvent(runId, routeId, address,
                "ROUTE_COMPLETED", null, null,
                overallSuccess ? "COMPLETED" : "PARTIAL_SUCCESS", null, summary);

        return new RouteExecutionResult(runId, routeId, overallSuccess, discoveredFiles.size(),
                successCount, failureCount, skippedCount, startTime, endTime, summary, completedRuns);
    }

    /**
     * Processes a single file. Returns true if transferred, false if skipped due to idempotency.
     */
    private boolean processSingleFile(RouteDefinition route, RetryPolicy retryPolicy,
                                      TransferRun transferRun, RemoteFileInfo remoteFile) throws Exception {
        String filename = remoteFile.getFilename();
        Path localDownloadPath = workspaceService.resolvePath(WorkspaceDirectory.DOWNLOADS, filename);

        // 1. Download with retry
        retryManager.execute(
                "DOWNLOAD",
                transferRun.getRunId(),
                route.getRouteId(),
                retryPolicy,
                () -> {
                    readAdapter.downloadFile(route.getSourceConnectionId(), remoteFile.getRemotePath(), localDownloadPath, true);
                    return null;
                }
        );

        // 2. Checksum and Idempotency verification
        long actualSize = Files.size(localDownloadPath);

        // Enforce minimum file size constraint (default 1024 bytes / 1 KB)
        if (actualSize < route.getMinFileSize()) {
            String rejectMsg = String.format("File [%s] rejected: size %d bytes is below minimum allowed threshold (%d bytes / 1 KB).",
                    filename, actualSize, route.getMinFileSize());
            log.warn(rejectMsg);
            throw new FileProcessingException("FILE_UNDERSIZED", rejectMsg);
        }

        String sourceChecksum = checksumService.calculateChecksum(localDownloadPath, route.getChecksumAlgorithm());
        transferRun.setSourceChecksum(sourceChecksum);
        transferRun.setBytesTransferred(actualSize);

        IdempotencyKey idempotencyKey = new IdempotencyKey(
                route.getRouteId(),
                route.getSourceConnectionId(),
                remoteFile.getRemotePath(),
                actualSize,
                sourceChecksum
        );

        // Check if duplicate
        if (idempotencyService.isAlreadyProcessed(idempotencyKey) && !route.isAllowReprocessing()) {
            log.debug("Duplicate file detected for route [{}] file [{}] (checksum: {}). Skipping transfer to destination.",
                    route.getRouteId(), filename, sourceChecksum);
            transferRun.setStatus(TransferStatus.SKIPPED_DUPLICATE);
            transferRun.setBytesTransferred(actualSize);
            transferRun.setEndTime(Instant.now());
            return false;
        }

        // Register in-progress idempotency
        idempotencyService.registerTransfer(idempotencyKey, transferRun.getRunId());

        try {
            // 3. Validate & Process
            statusService.updateStatus(transferRun.getRunId(), TransferStatus.PROCESSING);

            if (route.getProcessingMode() == ProcessingMode.RAW_COPY) {
                Path outputFile;
                String destFilename;
                String checksum;

                if (route.isCompressLargeFiles() && actualSize > route.getMaxStandardFileSize()) {
                    FileCompressionService.CompressionResult compResult = fileCompressionService.compress(
                            localDownloadPath,
                            transferRun.getRunId(),
                            route.getChecksumAlgorithm()
                    );
                    outputFile = compResult.compressedPath();
                    destFilename = compResult.destinationFilename();
                    checksum = compResult.compressedChecksum();

                    auditService.recordEvent(
                            transferRun.getRunId(),
                            route.getRouteId(),
                            resolveAddress(route),
                            "FILE_COMPRESSED",
                            remoteFile.getRemotePath(),
                            destFilename,
                            "COMPRESSED",
                            null,
                            String.format("Large file [%s] (%.2f MB) compressed to [%s] (%.2f MB, saved %.1f%%) [SHA-256: %s]",
                                    filename,
                                    compResult.originalSize() / (1024.0 * 1024.0),
                                    destFilename,
                                    compResult.compressedSize() / (1024.0 * 1024.0),
                                    compResult.reductionPercent(),
                                    checksum)
                    );
                } else {
                    RawCopyProcessor.RawCopyResult result = rawCopyProcessor.process(localDownloadPath, route.getChecksumAlgorithm());
                    outputFile = result.outputFile();
                    destFilename = filename;
                    checksum = result.checksum();
                }

                transferRun.setOutputChecksum(checksum);
                transferRun.setDestinationFilename(destFilename);

                // Publish output file with retry
                TransferResult pubResult = retryManager.execute(
                        "PUBLISH",
                        transferRun.getRunId(),
                        route.getRouteId(),
                        retryPolicy,
                        () -> writeAdapter.publishFile(
                                route.getDestinationConnectionId(),
                                outputFile,
                                route.getDestinationDirectory(),
                                destFilename,
                                true
                        )
                );

                transferRun.setBytesTransferred(pubResult.getBytesTransferred());
                transferRun.setStatus(TransferStatus.COMPLETED);
                transferRun.setEndTime(Instant.now());

                idempotencyService.markCompleted(idempotencyKey, pubResult.getFullRemotePath());

                String address = resolveAddress(route);
                auditService.recordEvent(transferRun.getRunId(), route.getRouteId(), address,
                        "TRANSFER_COMPLETED",
                        remoteFile.getRemotePath(), pubResult.getFullRemotePath(), "COMPLETED", null,
                        "Transferred " + pubResult.getBytesTransferred() + " bytes to " + pubResult.getFullRemotePath() + " [SHA-256: " + checksum + "]");

            } else if (route.getProcessingMode() == ProcessingMode.TRANSFORM) {
                TransformationProcessor.TransformationResult result = transformationProcessor.transform(
                        localDownloadPath,
                        route.getRouteId(),
                        transferRun.getRunId()
                );

                // Publish Phase A: Transformed data file (with .part rename) with retry
                TransferResult pubResult = retryManager.execute(
                        "PUBLISH_DATA",
                        transferRun.getRunId(),
                        route.getRouteId(),
                        retryPolicy,
                        () -> writeAdapter.publishFile(
                                route.getDestinationConnectionId(),
                                result.outputFile(),
                                route.getDestinationDirectory(),
                                result.outputFile().getFileName().toString(),
                                true
                        )
                );

                // Publish Phase B: Checksum companion file (.sha256)
                retryManager.execute(
                        "PUBLISH_COMPANION",
                        transferRun.getRunId(),
                        route.getRouteId(),
                        retryPolicy,
                        () -> writeAdapter.publishFile(
                                route.getDestinationConnectionId(),
                                result.companionFile(),
                                route.getDestinationDirectory(),
                                result.companionFile().getFileName().toString(),
                                true
                        )
                );

                // Publish Phase C: Manifest file (.manifest.json)
                retryManager.execute(
                        "PUBLISH_MANIFEST",
                        transferRun.getRunId(),
                        route.getRouteId(),
                        retryPolicy,
                        () -> writeAdapter.publishFile(
                                route.getDestinationConnectionId(),
                                result.manifestFile(),
                                route.getDestinationDirectory(),
                                result.manifestFile().getFileName().toString(),
                                true
                        )
                );

                transferRun.setRecordCount(result.recordCount());
                transferRun.setBytesTransferred(pubResult.getBytesTransferred());
                transferRun.setOutputChecksum(result.outputChecksum());
                transferRun.setDestinationFilename(result.outputFile().getFileName().toString());
                transferRun.setStatus(TransferStatus.COMPLETED);
                transferRun.setEndTime(Instant.now());

                idempotencyService.markCompleted(idempotencyKey, pubResult.getFullRemotePath());

                String address = resolveAddress(route);
                auditService.recordEvent(transferRun.getRunId(), route.getRouteId(), address,
                        "TRANSFORMATION_COMPLETED",
                        remoteFile.getRemotePath(), pubResult.getFullRemotePath(), "COMPLETED", null,
                        String.format("Transformed %d records to %s [SHA-256: %s]",
                                result.recordCount(), pubResult.getFullRemotePath(), result.outputChecksum()));
            } else {
                throw new MftException("UNSUPPORTED_MODE", "Processing mode not supported: " + route.getProcessingMode());
            }

            // Safe Post-Transfer Handshake: Archival and Source Cleanup
            // Zero data loss & Zero race condition guarantee:
            // Executed strictly after destination publication has been completely verified.
            performPostTransferArchivalAndCleanup(route, transferRun, remoteFile, localDownloadPath, actualSize, sourceChecksum);

            return true;
        } catch (Exception e) {
            idempotencyService.markFailed(idempotencyKey, e.getMessage());
            throw e;
        }
    }

    private String resolveAddress(RouteDefinition route) {
        if (route == null) return "localhost:2022/upload -> localhost:2222/incoming";
        String srcAddr = "localhost:2022";
        String dstAddr = "localhost:2222";
        if (connectionResolver != null) {
            try {
                com.mft.integration.connection.ConnectionDefinition src = connectionResolver.getConnection(route.getSourceConnectionId());
                if (src != null) srcAddr = src.getHost() + ":" + src.getPort();
            } catch (Exception ignored) {}
            try {
                com.mft.integration.connection.ConnectionDefinition dst = connectionResolver.getConnection(route.getDestinationConnectionId());
                if (dst != null) dstAddr = dst.getHost() + ":" + dst.getPort();
            } catch (Exception ignored) {}
        }
        String srcDir = route.getSourceDirectory() != null ? route.getSourceDirectory() : "/upload";
        String dstDir = route.getDestinationDirectory() != null ? route.getDestinationDirectory() : "/incoming";
        return srcAddr + srcDir + " -> " + dstAddr + dstDir;
    }

    private String generateRunId(String routeId) {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(Instant.now().atZone(java.time.ZoneOffset.UTC));
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s_%s_%s", routeId, timestamp, shortUuid);
    }

    /**
     * Executes safe post-transfer archival and source deletion.
     * Guarantees zero data loss: Only executed once destination publication has been confirmed.
     */
    private void performPostTransferArchivalAndCleanup(RouteDefinition route, TransferRun transferRun,
                                                      RemoteFileInfo remoteFile, Path localDownloadPath,
                                                      long actualSize, String sourceChecksum) {
        String filename = remoteFile.getFilename();
        String address = resolveAddress(route);

        // 1. Immutable Local Workspace Archive Backup
        if (route.isArchiveSourceAfterTransfer()) {
            try {
                Path localArchivePath = workspaceService.resolvePath(
                        WorkspaceDirectory.ARCHIVE,
                        transferRun.getRunId(),
                        filename
                );
                Files.createDirectories(localArchivePath.getParent());
                Files.copy(localDownloadPath, localArchivePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                log.info("Safe Post-Transfer: Archived local immutable backup for [{}] at [{}]", filename, localArchivePath);
                auditService.recordDetailedEvent(
                        transferRun.getRunId(),
                        route.getRouteId(),
                        route.getSourceConnectionId(),
                        null,
                        address,
                        localArchivePath.toString(),
                        "FILE_ARCHIVED_LOCAL",
                        remoteFile.getRemotePath(),
                        localArchivePath.toString(),
                        actualSize,
                        sourceChecksum,
                        null,
                        "ARCHIVED",
                        null,
                        String.format("Safe backup preserved in local archive [%s] (Size: %d bytes, SHA-256: %s)",
                                localArchivePath, actualSize, sourceChecksum)
                );
            } catch (Exception e) {
                log.warn("Failed to create local archive backup for [{}]: {}", filename, e.getMessage());
            }

            // 2. Remote Source Server Archival (if sourceArchiveDirectory is configured, e.g. /archive)
            if (route.getSourceArchiveDirectory() != null && !route.getSourceArchiveDirectory().isBlank()) {
                try {
                    String remoteArchivePath = com.mft.integration.util.PathValidationUtil.combineRemotePath(
                            route.getSourceArchiveDirectory(),
                            filename
                    );
                    readAdapter.moveFile(
                            route.getSourceConnectionId(),
                            remoteFile.getRemotePath(),
                            remoteArchivePath
                    );
                    log.info("Safe Post-Transfer: Archived remote source file [{}] to [{}:{}]",
                            remoteFile.getRemotePath(), route.getSourceConnectionId(), remoteArchivePath);
                    auditService.recordDetailedEvent(
                            transferRun.getRunId(),
                            route.getRouteId(),
                            route.getSourceConnectionId(),
                            route.getSourceConnectionId(),
                            address,
                            remoteArchivePath,
                            "FILE_ARCHIVED_REMOTE",
                            remoteFile.getRemotePath(),
                            remoteArchivePath,
                            actualSize,
                            sourceChecksum,
                            null,
                            "ARCHIVED",
                            null,
                            String.format("Moved source file to remote archive [%s:%s]",
                                    route.getSourceConnectionId(), remoteArchivePath)
                    );
                } catch (Exception e) {
                    log.error("Failed to archive source file on remote server [{}:{}]: {}",
                            route.getSourceConnectionId(), remoteFile.getRemotePath(), e.getMessage());
                }
            }
        }

        // 3. Source Pickup Deletion (if deleteSourceAfterTransfer is true AND it wasn't already moved to remote archive)
        if (route.isDeleteSourceAfterTransfer()) {
            boolean alreadyMoved = route.isArchiveSourceAfterTransfer()
                    && route.getSourceArchiveDirectory() != null
                    && !route.getSourceArchiveDirectory().isBlank();
            if (!alreadyMoved) {
                try {
                    readAdapter.deleteFile(route.getSourceConnectionId(), remoteFile.getRemotePath());
                    log.info("Safe Post-Transfer: Deleted original source file [{}] on [{}] after verified delivery",
                            remoteFile.getRemotePath(), route.getSourceConnectionId());
                    auditService.recordDetailedEvent(
                            transferRun.getRunId(),
                            route.getRouteId(),
                            route.getSourceConnectionId(),
                            null,
                            address,
                            null,
                            "SOURCE_FILE_DELETED",
                            remoteFile.getRemotePath(),
                            null,
                            actualSize,
                            sourceChecksum,
                            null,
                            "DELETED",
                            null,
                            String.format("Source file [%s] removed from pickup folder on [%s] following verified destination receipt",
                                    remoteFile.getRemotePath(), route.getSourceConnectionId())
                    );
                } catch (Exception e) {
                    log.error("Failed to delete source file after delivery [{}:{}]: {}",
                            route.getSourceConnectionId(), remoteFile.getRemotePath(), e.getMessage());
                }
            }
        }
    }
}
