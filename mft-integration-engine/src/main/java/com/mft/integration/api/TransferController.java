package com.mft.integration.api;

import com.mft.integration.api.dto.ApiResponse;
import com.mft.integration.api.dto.AuditEventDto;
import com.mft.integration.api.dto.TransferRunDto;
import com.mft.integration.audit.AuditEvent;
import com.mft.integration.audit.AuditService;
import com.mft.integration.exception.MftException;
import com.mft.integration.idempotency.IdempotencyService;
import com.mft.integration.status.TransferRun;
import com.mft.integration.status.TransferStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for monitoring transfer run lifecycles and querying structured audit events.
 */
@Tag(name = "Transfers & Audit", description = "Monitor transfer status, view run execution histories, and inspect audit logs")
@RestController
@RequestMapping("/api")
public class TransferController {

    private final TransferStatusService statusService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;

    public TransferController(TransferStatusService statusService, AuditService auditService, IdempotencyService idempotencyService) {
        this.statusService = statusService;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
    }

    @Operation(summary = "List transfer runs", description = "Retrieves transfer execution runs, optionally filtered by route ID.")
    @GetMapping("/transfers")
    public ResponseEntity<ApiResponse<List<TransferRunDto>>> getTransfers(
            @Parameter(description = "Optional route ID to filter runs") @RequestParam(required = false) String routeId) {

        List<TransferRun> runs = (routeId != null && !routeId.isBlank())
                ? statusService.getRunsForRoute(routeId)
                : statusService.getAllRuns();

        List<TransferRunDto> dtos = runs.stream()
                .map(TransferRunDto::new)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @Operation(summary = "Get single transfer run", description = "Fetches status details for a specific transfer run by runId.")
    @GetMapping("/transfers/{runId}")
    public ResponseEntity<ApiResponse<TransferRunDto>> getTransfer(@Parameter(description = "Transfer run ID") @PathVariable String runId) {
        TransferRun run = statusService.getRun(runId)
                .orElseThrow(() -> new MftException("TRANSFER_RUN_NOT_FOUND", "Transfer run not found: " + runId));

        return ResponseEntity.ok(ApiResponse.ok(new TransferRunDto(run)));
    }

    @Operation(summary = "Get audit events for a transfer run", description = "Retrieves all audit events associated with a specific transfer run ID.")
    @GetMapping("/transfers/{runId}/audit")
    public ResponseEntity<ApiResponse<List<AuditEventDto>>> getAuditForRun(@Parameter(description = "Transfer run ID") @PathVariable String runId) {
        List<AuditEvent> events = auditService.getEventsForRun(runId);
        List<AuditEventDto> dtos = events.stream()
                .map(AuditEventDto::new)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @Operation(summary = "Search all audit events", description = "Queries audit trail events across routes, connections, and transfer runs.")
    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<List<AuditEventDto>>> getAuditEvents(
            @Parameter(description = "Optional route ID filter") @RequestParam(required = false) String routeId,
            @Parameter(description = "Optional run ID filter") @RequestParam(required = false) String runId) {

        List<AuditEvent> events;
        if (runId != null && !runId.isBlank()) {
            events = auditService.getEventsForRun(runId);
        } else if (routeId != null && !routeId.isBlank()) {
            events = auditService.getEventsForRoute(routeId);
        } else {
            events = auditService.getAllEvents();
        }

        List<AuditEventDto> dtos = events.stream()
                .map(AuditEventDto::new)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @Operation(summary = "Clear idempotency cache", description = "Resets the idempotency tracking database to allow re-transfer of existing files.")
    @PostMapping("/idempotency/clear")
    public ResponseEntity<ApiResponse<String>> clearIdempotencyCache() {
        idempotencyService.clearRecords();
        return ResponseEntity.ok(ApiResponse.ok("Idempotency duplicate cache cleared. Previously sent files can now be re-transferred.", "CLEARED"));
    }
}
