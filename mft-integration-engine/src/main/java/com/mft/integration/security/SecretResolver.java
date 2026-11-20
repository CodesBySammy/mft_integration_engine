package com.mft.integration.security;

import java.util.Optional;

/**
 * Interface for safely resolving credential secrets from secure providers.
 */
public interface SecretResolver {

    /**
     * Resolves a secret value by reference key.
     *
     * @param secretReference name of environment variable or vault key
     * @return resolved secret value if present
     */
    Optional<String> resolveSecret(String secretReference);
}
