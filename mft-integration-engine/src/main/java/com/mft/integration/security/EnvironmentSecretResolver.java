package com.mft.integration.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves secrets from OS environment variables.
 * Never logs actual secret values.
 */
@Component
public class EnvironmentSecretResolver implements SecretResolver {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentSecretResolver.class);

    @Override
    public Optional<String> resolveSecret(String secretReference) {
        if (secretReference == null || secretReference.isBlank()) {
            return Optional.empty();
        }

        // 1. Look up directly in OS environment
        String value = System.getenv(secretReference);
        if (value != null && !value.isBlank()) {
            log.debug("Resolved secret for key reference [{}] from environment variables", secretReference);
            return Optional.of(value);
        }

        // 2. Look up in Java System properties
        value = System.getProperty(secretReference);
        if (value != null && !value.isBlank()) {
            log.debug("Resolved secret for key reference [{}] from system properties", secretReference);
            return Optional.of(value);
        }

        log.debug("No secret found for key reference [{}]", secretReference);
        return Optional.empty();
    }
}
