package com.mft.integration.api;

import com.mft.integration.api.dto.ApiResponse;
import com.mft.integration.persistence.DatabasePersistenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for inspecting relational database status and querying raw database tables.
 */
@Tag(name = "Database", description = "Query relational database tables (H2/MySQL) directly via REST / Swagger")
@RestController
@RequestMapping("/api/database")
public class DatabaseController {

    private final DatabasePersistenceService databasePersistenceService;

    public DatabaseController(DatabasePersistenceService databasePersistenceService) {
        this.databasePersistenceService = databasePersistenceService;
    }

    @Operation(summary = "Get database status", description = "Inspects live relational database connectivity, engine type, URL, and table counts.")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        return ResponseEntity.ok(ApiResponse.ok(databasePersistenceService.getDatabaseStatus()));
    }

    @Operation(summary = "Query audit events from relational DB", description = "Fetches recent audit log rows directly from mft_audit_events table.")
    @GetMapping("/audit-events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAuditEvents(
            @Parameter(description = "Max records to return") @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(databasePersistenceService.queryAuditEvents(limit)));
    }

    @Operation(summary = "Query transfer runs from relational DB", description = "Fetches transfer run records directly from mft_transfer_runs table.")
    @GetMapping("/transfer-runs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTransferRuns(
            @Parameter(description = "Max records to return") @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(databasePersistenceService.queryTransferRuns(limit)));
    }

    @Operation(summary = "Query idempotency records from relational DB", description = "Fetches records from mft_idempotency_records table.")
    @GetMapping("/idempotency-records")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getIdempotencyRecords(
            @Parameter(description = "Max records to return") @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(databasePersistenceService.queryIdempotencyRecords(limit)));
    }
}
