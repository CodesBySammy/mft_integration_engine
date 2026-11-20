package com.mft.integration.connection;

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
 * Central registry of SFTP connection definitions.
 * Populates from configuration on startup and supports dynamic registration.
 */
@Component
public class ConnectionRegistry implements ConnectionResolver {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    private final Map<String, ConnectionDefinition> registry = new ConcurrentHashMap<>();
    private final ConnectionValidator validator;
    private final MftProperties properties;

    public ConnectionRegistry(ConnectionValidator validator, MftProperties properties) {
        this.validator = validator;
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        if (properties.getConnections() != null) {
            properties.getConnections().forEach((key, def) -> {
                if (def.getConnectionId() == null || def.getConnectionId().isBlank()) {
                    def.setConnectionId(key);
                }
                register(def);
            });
        }
        log.info("Initialized ConnectionRegistry with {} active connection definition(s).", registry.size());
    }

    /**
     * Registers a new or updated connection definition with fail-fast validation.
     */
    public void register(ConnectionDefinition definition) {
        validator.validate(definition);
        registry.put(definition.getConnectionId(), definition);
        log.info("Registered connection definition: id={}, host={}:{}, user={}, authType={}, enabled={}",
                definition.getConnectionId(),
                definition.getHost(),
                definition.getPort(),
                definition.getUsername(),
                definition.getAuthenticationType(),
                definition.isEnabled());
    }

    @Override
    public Optional<ConnectionDefinition> findConnection(String connectionId) {
        if (connectionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry.get(connectionId));
    }

    @Override
    public ConnectionDefinition getConnection(String connectionId) {
        ConnectionDefinition def = registry.get(connectionId);
        if (def == null) {
            throw new MftException("CONNECTION_NOT_FOUND", "No connection registered with ID: " + connectionId);
        }
        if (!def.isEnabled()) {
            throw new MftException("CONNECTION_DISABLED", "Connection [" + connectionId + "] is disabled.");
        }
        return def;
    }

    @Override
    public List<ConnectionDefinition> getAllConnections() {
        return Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }

    public boolean contains(String connectionId) {
        return registry.containsKey(connectionId);
    }

    public void remove(String connectionId) {
        registry.remove(connectionId);
        log.info("Removed connection definition: id={}", connectionId);
    }
}
