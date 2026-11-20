package com.mft.integration.security;

import com.mft.integration.connection.AuthenticationType;
import com.mft.integration.connection.ConnectionDefinition;
import com.mft.integration.exception.ValidationException;
import org.springframework.stereotype.Component;

/**
 * Provides resolved credentials for a connection definition without exposing
 * secrets in logs, DTOs, or memory dumps.
 */
@Component
public class CredentialProvider {

    private final SecretResolver secretResolver;

    public CredentialProvider(SecretResolver secretResolver) {
        this.secretResolver = secretResolver;
    }

    /**
     * Resolves the effective password for a password-based connection.
     * Order of resolution:
     * 1. If passwordSecretRef is specified, resolve via SecretResolver.
     * 2. If direct password property is populated (e.g. from local profile environment interpolation), use it.
     */
    public String resolvePassword(ConnectionDefinition definition) {
        if (definition.getAuthenticationType() != AuthenticationType.PASSWORD) {
            return null;
        }

        // 1. Try resolving through secret reference (e.g. OS environment variable or vault)
        if (definition.getPasswordSecretRef() != null && !definition.getPasswordSecretRef().isBlank()) {
            java.util.Optional<String> secret = secretResolver.resolveSecret(definition.getPasswordSecretRef());
            if (secret.isPresent()) {
                return secret.get();
            }
        }

        // 2. Fall back to direct configured password if present
        if (definition.getPassword() != null && !definition.getPassword().isBlank()) {
            return definition.getPassword();
        }

        throw new ValidationException("MISSING_CREDENTIALS",
                "Connection [" + definition.getConnectionId() + "] is configured for PASSWORD authentication but no password could be resolved (checked secret reference and default property).");
    }

    /**
     * Resolves the effective private key content or path for private key authentication.
     */
    public String resolvePrivateKey(ConnectionDefinition definition) {
        if (definition.getAuthenticationType() != AuthenticationType.PRIVATE_KEY) {
            return null;
        }

        // 1. Try resolving through secret reference
        if (definition.getPrivateKeySecretRef() != null && !definition.getPrivateKeySecretRef().isBlank()) {
            java.util.Optional<String> secret = secretResolver.resolveSecret(definition.getPrivateKeySecretRef());
            if (secret.isPresent()) {
                return secret.get();
            }
        }

        // 2. Fall back to direct configured key path
        if (definition.getPrivateKeyPath() != null && !definition.getPrivateKeyPath().isBlank()) {
            return definition.getPrivateKeyPath();
        }

        throw new ValidationException("MISSING_CREDENTIALS",
                "Connection [" + definition.getConnectionId() + "] is configured for PRIVATE_KEY authentication but no private key could be resolved (checked secret reference and default path).");
    }
}
