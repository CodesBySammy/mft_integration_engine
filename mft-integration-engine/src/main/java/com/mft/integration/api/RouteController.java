package com.mft.integration.api;

import com.mft.integration.api.dto.ApiResponse;
import com.mft.integration.api.dto.RouteDto;
import com.mft.integration.api.dto.RouteExecutionRequest;
import com.mft.integration.api.dto.RouteExecutionResponseDto;
import com.mft.integration.orchestrator.RouteDefinition;
import com.mft.integration.orchestrator.RouteExecutionResult;
import com.mft.integration.orchestrator.RouteOrchestrator;
import com.mft.integration.orchestrator.RouteRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for inspecting configured transfer routes and triggering executions.
 */
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteRegistry routeRegistry;
    private final RouteOrchestrator routeOrchestrator;

    public RouteController(RouteRegistry routeRegistry, RouteOrchestrator routeOrchestrator) {
        this.routeRegistry = routeRegistry;
        this.routeOrchestrator = routeOrchestrator;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteDto>>> getAllRoutes() {
        List<RouteDto> routes = routeRegistry.getAllRoutes().stream()
                .map(RouteDto::new)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(routes));
    }

    @GetMapping("/{routeId}")
    public ResponseEntity<ApiResponse<RouteDto>> getRoute(@PathVariable String routeId) {
        RouteDefinition route = routeRegistry.getRoute(routeId);
        return ResponseEntity.ok(ApiResponse.ok(new RouteDto(route)));
    }

    @PostMapping("/{routeId}/execute")
    public ResponseEntity<ApiResponse<RouteExecutionResponseDto>> executeRoute(
            @PathVariable String routeId,
            @RequestBody(required = false) RouteExecutionRequest request) {

        String patternOverride = request != null ? request.getPatternOverride() : null;
        RouteExecutionResult result = routeOrchestrator.executeRoute(routeId, patternOverride);
        RouteExecutionResponseDto responseDto = new RouteExecutionResponseDto(result);

        String message = result.isSuccess()
                ? "Route executed successfully: " + result.getMessage()
                : "Route execution completed with errors: " + result.getMessage();

        return ResponseEntity.ok(ApiResponse.ok(message, responseDto));
    }

    @PostMapping("/{routeId}/toggle-reprocessing")
    public ResponseEntity<ApiResponse<RouteDto>> toggleReprocessing(@PathVariable String routeId) {
        RouteDefinition route = routeRegistry.getRoute(routeId);
        route.setAllowReprocessing(!route.isAllowReprocessing());
        return ResponseEntity.ok(ApiResponse.ok("Reprocessing set to: " + route.isAllowReprocessing(), new RouteDto(route)));
    }
}
