package com.mft.integration.api;

import com.mft.integration.api.dto.ApiResponse;
import com.mft.integration.api.dto.RouteDto;
import com.mft.integration.api.dto.RouteExecutionRequest;
import com.mft.integration.api.dto.RouteExecutionResponseDto;
import com.mft.integration.orchestrator.RouteDefinition;
import com.mft.integration.orchestrator.RouteExecutionResult;
import com.mft.integration.orchestrator.RouteOrchestrator;
import com.mft.integration.orchestrator.RouteRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for inspecting configured transfer routes and triggering executions.
 */
@Tag(name = "Routes", description = "Endpoints for inspecting routes, querying configurations, and triggering executions")
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteRegistry routeRegistry;
    private final RouteOrchestrator routeOrchestrator;
    private final Map<String, RouteExecutionResponseDto> apiIdempotencyCache = new ConcurrentHashMap<>();

    public RouteController(RouteRegistry routeRegistry, RouteOrchestrator routeOrchestrator) {
        this.routeRegistry = routeRegistry;
        this.routeOrchestrator = routeOrchestrator;
    }

    @Operation(summary = "List all configured routes", description = "Retrieves all configured MFT transfer routes including source, destination, and file patterns.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteDto>>> getAllRoutes() {
        List<RouteDto> routes = routeRegistry.getAllRoutes().stream()
                .map(RouteDto::new)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(routes));
    }

    @Operation(summary = "Get route details", description = "Fetches configuration details for a single transfer route by ID.")
    @GetMapping("/{routeId}")
    public ResponseEntity<ApiResponse<RouteDto>> getRoute(@Parameter(description = "Route identifier (e.g. R_RAW_COPY)") @PathVariable String routeId) {
        RouteDefinition route = routeRegistry.getRoute(routeId);
        return ResponseEntity.ok(ApiResponse.ok(new RouteDto(route)));
    }

    @Operation(summary = "Trigger route execution", description = "Executes an MFT transfer route on demand with optional filename pattern override and idempotency key.")
    @PostMapping("/{routeId}/execute")
    public ResponseEntity<ApiResponse<RouteExecutionResponseDto>> executeRoute(
            @Parameter(description = "Route identifier") @PathVariable String routeId,
            @Parameter(description = "Optional idempotency key to prevent duplicate API submissions") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) RouteExecutionRequest request) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            RouteExecutionResponseDto cached = apiIdempotencyCache.get(idempotencyKey);
            if (cached != null) {
                return ResponseEntity.ok(ApiResponse.ok("Idempotent response for key: " + idempotencyKey, cached));
            }
        }

        String patternOverride = request != null ? request.getPatternOverride() : null;
        RouteExecutionResult result = routeOrchestrator.executeRoute(routeId, patternOverride);
        RouteExecutionResponseDto responseDto = new RouteExecutionResponseDto(result);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            apiIdempotencyCache.put(idempotencyKey, responseDto);
        }

        String message = result.isSuccess()
                ? "Route executed successfully: " + result.getMessage()
                : "Route execution completed with errors: " + result.getMessage();

        return ResponseEntity.ok(ApiResponse.ok(message, responseDto));
    }

    @Operation(summary = "Toggle route reprocessing flag", description = "Enables or disables reprocessing mode for a route to allow re-transferring previously processed files.")
    @PostMapping("/{routeId}/toggle-reprocessing")
    public ResponseEntity<ApiResponse<RouteDto>> toggleReprocessing(@Parameter(description = "Route identifier") @PathVariable String routeId) {
        RouteDefinition route = routeRegistry.getRoute(routeId);
        route.setAllowReprocessing(!route.isAllowReprocessing());
        return ResponseEntity.ok(ApiResponse.ok("Reprocessing set to: " + route.isAllowReprocessing(), new RouteDto(route)));
    }
}
