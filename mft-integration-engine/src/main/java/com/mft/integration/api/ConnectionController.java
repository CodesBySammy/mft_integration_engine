package com.mft.integration.api;

import com.mft.integration.adapter.read.ReadAdapter;
import com.mft.integration.api.dto.ApiResponse;
import com.mft.integration.api.dto.ConnectionDto;
import com.mft.integration.api.dto.ConnectionTestResultDto;
import com.mft.integration.connection.ConnectionDefinition;
import com.mft.integration.connection.ConnectionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for inspecting SFTP connection definitions and verifying live endpoint reachability.
 */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionRegistry connectionRegistry;
    private final ReadAdapter readAdapter;

    public ConnectionController(ConnectionRegistry connectionRegistry, ReadAdapter readAdapter) {
        this.connectionRegistry = connectionRegistry;
        this.readAdapter = readAdapter;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConnectionDto>>> getAllConnections() {
        List<ConnectionDto> connections = connectionRegistry.getAllConnections().stream()
                .map(ConnectionDto::new)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(connections));
    }

    @GetMapping("/{connectionId}")
    public ResponseEntity<ApiResponse<ConnectionDto>> getConnection(@PathVariable String connectionId) {
        ConnectionDefinition connection = connectionRegistry.getConnection(connectionId);
        return ResponseEntity.ok(ApiResponse.ok(new ConnectionDto(connection)));
    }

    @GetMapping("/{connectionId}/test")
    public ResponseEntity<ApiResponse<ConnectionTestResultDto>> testConnection(@PathVariable String connectionId) {
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
