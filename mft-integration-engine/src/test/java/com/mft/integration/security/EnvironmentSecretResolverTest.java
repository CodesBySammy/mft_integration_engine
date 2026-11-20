package com.mft.integration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentSecretResolverTest {

    private final EnvironmentSecretResolver resolver = new EnvironmentSecretResolver();

    @Test
    @DisplayName("Empty or null reference returns Optional.empty()")
    void testBlankReferenceReturnsEmpty() {
        assertThat(resolver.resolveSecret(null)).isEmpty();
        assertThat(resolver.resolveSecret("")).isEmpty();
        assertThat(resolver.resolveSecret("   ")).isEmpty();
    }

    @Test
    @DisplayName("Resolves secret from Java system property")
    void testResolveFromSystemProperty() {
        String testKey = "TEST_SFTP_SECRET_KEY";
        String testVal = "super_secure_pass_42";
        System.setProperty(testKey, testVal);
        try {
            Optional<String> result = resolver.resolveSecret(testKey);
            assertThat(result).isPresent().contains(testVal);
        } finally {
            System.clearProperty(testKey);
        }
    }
}
