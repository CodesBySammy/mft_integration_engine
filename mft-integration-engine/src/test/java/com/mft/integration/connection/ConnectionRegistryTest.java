package com.mft.integration.connection;

import com.mft.integration.config.MftProperties;
import com.mft.integration.exception.MftException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionRegistryTest {

    private ConnectionRegistry registry;
    private ConnectionValidator validator;
    private MftProperties properties;

    @BeforeEach
    void setUp() {
        validator = new ConnectionValidator();
        properties = new MftProperties();
        registry = new ConnectionRegistry(validator, properties);
    }

    private ConnectionDefinition createSampleConnection(String id, boolean enabled) {
        ConnectionDefinition def = new ConnectionDefinition();
        def.setConnectionId(id);
        def.setHost("localhost");
        def.setPort(2022);
        def.setUsername("user");
        def.setPassword("pass");
        def.setAuthenticationType(AuthenticationType.PASSWORD);
        def.setEnabled(enabled);
        return def;
    }

    @Test
    @DisplayName("Register and retrieve connection by ID")
    void testRegisterAndRetrieve() {
        ConnectionDefinition def = createSampleConnection("C_FILE_IN", true);
        registry.register(def);

        assertThat(registry.contains("C_FILE_IN")).isTrue();
        Optional<ConnectionDefinition> found = registry.findConnection("C_FILE_IN");
        assertThat(found).isPresent();
        assertThat(found.get().getHost()).isEqualTo("localhost");

        ConnectionDefinition resolved = registry.getConnection("C_FILE_IN");
        assertThat(resolved).isNotNull();
        assertThat(resolved.getConnectionId()).isEqualTo("C_FILE_IN");
    }

    @Test
    @DisplayName("Get unknown connection throws CONNECTION_NOT_FOUND")
    void testGetUnknownConnectionThrows() {
        assertThatThrownBy(() -> registry.getConnection("NON_EXISTENT"))
                .isInstanceOf(MftException.class)
                .hasMessageContaining("No connection registered with ID: NON_EXISTENT");
    }

    @Test
    @DisplayName("Get disabled connection throws CONNECTION_DISABLED")
    void testGetDisabledConnectionThrows() {
        ConnectionDefinition def = createSampleConnection("DISABLED_CONN", false);
        registry.register(def);

        assertThatThrownBy(() -> registry.getConnection("DISABLED_CONN"))
                .isInstanceOf(MftException.class)
                .hasMessageContaining("is disabled");
    }

    @Test
    @DisplayName("Initialize from MftProperties populates registry")
    void testInitializeFromProperties() {
        Map<String, ConnectionDefinition> map = new HashMap<>();
        ConnectionDefinition c1 = createSampleConnection("C1", true);
        ConnectionDefinition c2 = createSampleConnection("C2", true);
        map.put("C1", c1);
        map.put("C2", c2);
        properties.setConnections(map);

        registry.initialize();

        assertThat(registry.getAllConnections()).hasSize(2);
        assertThat(registry.findConnection("C1")).isPresent();
        assertThat(registry.findConnection("C2")).isPresent();
    }
}
