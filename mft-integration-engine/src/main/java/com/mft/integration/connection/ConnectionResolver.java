package com.mft.integration.connection;

import java.util.List;
import java.util.Optional;

/**
 * Resolver interface for obtaining connection definitions at runtime by connectionId.
 */
public interface ConnectionResolver {

    /**
     * Finds connection definition by id.
     */
    Optional<ConnectionDefinition> findConnection(String connectionId);

    /**
     * Gets connection definition by id, or throws MftException if not found or disabled.
     */
    ConnectionDefinition getConnection(String connectionId);

    /**
     * Lists all registered connection definitions.
     */
    List<ConnectionDefinition> getAllConnections();
}
