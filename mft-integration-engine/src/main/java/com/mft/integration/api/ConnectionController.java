package com.mft.integration.api;

import com.mft.integration.adapter.read.ReadAdapter;
import com.mft.integration.api.dto.ApiResponse;
import com.mft.integration.api.dto.ConnectionDto;
import com.mft.integration.api.dto.ConnectionTestResultDto;
import com.mft.integration.connection.ConnectionDefinition;
import com.mft.integration.connection.ConnectionRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for inspecting SFTP connection definitions and verifying live endpoint reachability.
 */
@Tag(name = "Connections", description = "SFTP connection management and connectivity diagnostic health checks")
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionRegistry connectionRegistry;
    private final ReadAdapter readAdapter;

    public ConnectionController(ConnectionRegistry connectionRegistry, ReadAdapter readAdapter) {
        this.connectionRegistry = connectionRegistry;
        this.readAdapter = readAdapter;
    }

    @Operation(summary = "List all connections", description = "Retrieves all configured SFTP connection endpoints and credentials metadata.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ConnectionDto>>> getAllConnections() {
        List<ConnectionDto> connections = connectionRegistry.getAllConnections().stream()
                .map(ConnectionDto::new)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(connections));
    }

    @Operation(summary = "Get connection details", description = "Fetches configuration details for a single SFTP connection.")
    @GetMapping("/{connectionId}")
    public ResponseEntity<ApiResponse<ConnectionDto>> getConnection(@Parameter(description = "Connection identifier (e.g. C_FILE_IN)") @PathVariable String connectionId) {
        ConnectionDefinition connection = connectionRegistry.getConnection(connectionId);
        return ResponseEntity.ok(ApiResponse.ok(new ConnectionDto(connection)));
    }

    @Operation(summary = "Test SFTP connectivity", description = "Performs a live SSH handshake test to verify server reachability, port availability, and credentials.")
    @GetMapping("/{connectionId}/test")
    public ResponseEntity<ApiResponse<ConnectionTestResultDto>> testConnection(@Parameter(description = "Connection identifier") @PathVariable String connectionId) {
        ConnectionDefinition connection = connectionRegistry.getConnection(connectionId);
        long start = System.currentTimeMillis();
        boolean reachable = readAdapter.testConnection(connectionId);
        long responseTime = System.currentTimeMillis() - start;

        String message = reachable
                ? String.format("Successfully connected to SFTP endpoint %s:%d in %d ms", connection.getHost(), connection.getPort(), responseTime)
                : String.format("Failed to reach SFTP endpoint %s:%d", connection.getHost(), connection.getPort());

        ConnectionTestResultDto result = new ConnectionTestResultDto(
                connectionId,
                connection.getHost(),
                connection.getPort(),
                reachable,
                responseTime,
                message
        );

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
