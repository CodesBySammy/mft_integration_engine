package com.mft.integration.api;

import com.mft.integration.api.dto.ApiResponse;
import com.mft.integration.api.dto.HealthCheckDto;
import com.mft.integration.connection.ConnectionRegistry;
import com.mft.integration.orchestrator.RouteRegistry;
import com.mft.integration.workspace.LocalWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check REST controller reporting engine status, active connections, and workspace health.
 */
@Tag(name = "Health & System", description = "Operational health checks and system telemetry")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ConnectionRegistry connectionRegistry;
    private final RouteRegistry routeRegistry;
    private final LocalWorkspaceService workspaceService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.mft.integration.persistence.DatabasePersistenceService databasePersistenceService;

    public HealthController(ConnectionRegistry connectionRegistry,
                            RouteRegistry routeRegistry,
                            LocalWorkspaceService workspaceService) {
        this.connectionRegistry = connectionRegistry;
        this.routeRegistry = routeRegistry;
        this.workspaceService = workspaceService;
    }

    @Operation(summary = "Engine health check", description = "Returns active system status, count of configured connections, registered routes, workspace readiness, and relational database status.")
    @GetMapping
    public ResponseEntity<ApiResponse<HealthCheckDto>> getHealth() {
        int connCount = connectionRegistry.getAllConnections().size();
        int routeCount = routeRegistry.getAllRoutes().size();
        boolean workspaceReady = workspaceService.getWorkspaceRoot() != null;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("workspaceRoot", workspaceService.getWorkspaceRoot().toString());
        details.put("configuredConnections", connCount);
        details.put("configuredRoutes", routeCount);
        if (databasePersistenceService != null) {
            details.put("database", databasePersistenceService.getDatabaseStatus());
        }
        details.put("status", "UP");

        HealthCheckDto health = new HealthCheckDto(
                "UP",
                "mft-integration-engine",
                connCount,
                routeCount,
                workspaceReady,
                details
        );

        return ResponseEntity.ok(ApiResponse.ok("System healthy", health));
    }

    @Operation(summary = "Relational database status", description = "Inspects live relational database connectivity (H2/MySQL), driver info, and table row counts.")
    @GetMapping("/database")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDatabaseStatus() {
        if (databasePersistenceService != null) {
            return ResponseEntity.ok(ApiResponse.ok(databasePersistenceService.getDatabaseStatus()));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("databaseConnected", false, "mode", "FILE_ONLY")));
    }
}
