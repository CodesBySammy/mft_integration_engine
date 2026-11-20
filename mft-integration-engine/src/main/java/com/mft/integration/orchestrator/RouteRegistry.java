package com.mft.integration.orchestrator;

import com.mft.integration.config.MftProperties;
import com.mft.integration.exception.MftException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding active route definitions loaded from configuration or registered dynamically.
 */
@Component
public class RouteRegistry {

    private static final Logger log = LoggerFactory.getLogger(RouteRegistry.class);

    private final Map<String, RouteDefinition> registry = new ConcurrentHashMap<>();
    private final MftProperties properties;

    public RouteRegistry(MftProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        if (properties.getRoutes() != null) {
            properties.getRoutes().forEach((key, def) -> {
                if (def.getRouteId() == null || def.getRouteId().isBlank()) {
                    def.setRouteId(key);
                }
                register(def);
            });
        }
        log.info("Initialized RouteRegistry with {} active route(s).", registry.size());
    }

    public void register(RouteDefinition route) {
        if (route == null || route.getRouteId() == null || route.getRouteId().isBlank()) {
            throw new MftException("INVALID_ROUTE", "Route or routeId must not be null or blank.");
        }
        registry.put(route.getRouteId(), route);
        log.info("Registered route: id={}, mode={}, src={} ({}) -> dst={} ({}), enabled={}",
                route.getRouteId(), route.getProcessingMode(),
                route.getSourceConnectionId(), route.getSourceDirectory(),
                route.getDestinationConnectionId(), route.getDestinationDirectory(),
                route.isEnabled());
    }

    public Optional<RouteDefinition> findRoute(String routeId) {
        if (routeId == null) return Optional.empty();
        return Optional.ofNullable(registry.get(routeId));
    }

    public RouteDefinition getRoute(String routeId) {
        RouteDefinition route = registry.get(routeId);
        if (route == null) {
            throw new MftException("ROUTE_NOT_FOUND", "No route registered with ID: " + routeId);
        }
        if (!route.isEnabled()) {
            throw new MftException("ROUTE_DISABLED", "Route [" + routeId + "] is disabled.");
        }
        return route;
    }

    public List<RouteDefinition> getAllRoutes() {
        return Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }
}
