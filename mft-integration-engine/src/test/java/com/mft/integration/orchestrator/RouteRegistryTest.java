package com.mft.integration.orchestrator;

import com.mft.integration.checksum.ChecksumAlgorithm;
import com.mft.integration.config.MftProperties;
import com.mft.integration.exception.MftException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteRegistryTest {

    private RouteRegistry routeRegistry;
    private MftProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MftProperties();
        routeRegistry = new RouteRegistry(properties);
    }

    private RouteDefinition createRoute(String id, boolean enabled) {
        RouteDefinition r = new RouteDefinition();
        r.setRouteId(id);
        r.setSourceConnectionId("C_FILE_IN");
        r.setDestinationConnectionId("C_FILE_OUT");
        r.setProcessingMode(ProcessingMode.RAW_COPY);
        r.setEnabled(enabled);
        return r;
    }

    @Test
    @DisplayName("Registers and retrieves route definition")
    void testRegisterAndRetrieve() {
        RouteDefinition route = createRoute("R_RAW_COPY", true);
        routeRegistry.register(route);

        assertThat(routeRegistry.findRoute("R_RAW_COPY")).isPresent();
        RouteDefinition retrieved = routeRegistry.getRoute("R_RAW_COPY");
        assertThat(retrieved.getSourceConnectionId()).isEqualTo("C_FILE_IN");
    }

    @Test
    @DisplayName("Throws when route is not found")
    void testNotFoundThrows() {
        assertThatThrownBy(() -> routeRegistry.getRoute("UNKNOWN_ROUTE"))
                .isInstanceOf(MftException.class)
                .hasMessageContaining("No route registered with ID: UNKNOWN_ROUTE");
    }

    @Test
    @DisplayName("Throws when route is disabled")
    void testDisabledThrows() {
        RouteDefinition disabledRoute = createRoute("R_DISABLED", false);
        routeRegistry.register(disabledRoute);

        assertThatThrownBy(() -> routeRegistry.getRoute("R_DISABLED"))
                .isInstanceOf(MftException.class)
                .hasMessageContaining("is disabled");
    }

    @Test
    @DisplayName("Initializes from MftProperties")
    void testInitializeFromProperties() {
        Map<String, RouteDefinition> routes = new HashMap<>();
        routes.put("R1", createRoute("R1", true));
        routes.put("R2", createRoute("R2", true));
        properties.setRoutes(routes);

        routeRegistry.initialize();

        assertThat(routeRegistry.getAllRoutes()).hasSize(2);
    }
}
