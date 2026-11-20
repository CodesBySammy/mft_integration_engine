package com.mft.integration.config;

import com.mft.integration.connection.ConnectionDefinition;
import com.mft.integration.orchestrator.RouteDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root configuration properties under 'mft' namespace.
 */
@Configuration
@ConfigurationProperties(prefix = "mft")
public class MftProperties {

    /**
     * Map of connectionId -> ConnectionDefinition
     */
    private Map<String, ConnectionDefinition> connections = new LinkedHashMap<>();

    /**
     * Map of routeId -> RouteDefinition
     */
    private Map<String, RouteDefinition> routes = new LinkedHashMap<>();

    public Map<String, ConnectionDefinition> getConnections() {
        return connections;
    }

    public void setConnections(Map<String, ConnectionDefinition> connections) {
        this.connections = connections;
    }

    public Map<String, RouteDefinition> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, RouteDefinition> routes) {
        this.routes = routes;
    }
}
