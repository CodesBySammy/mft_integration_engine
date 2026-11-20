package com.mft.integration.security;

import com.mft.integration.connection.AuthenticationType;
import com.mft.integration.connection.ConnectionDefinition;
import com.mft.integration.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialProviderTest {

    private SecretResolver secretResolver;
    private CredentialProvider credentialProvider;

    @BeforeEach
    void setUp() {
        secretResolver = secretRef -> {
            if ("KNOWN_SECRET".equals(secretRef)) {
                return Optional.of("resolved_secret_value");
            }
            return Optional.empty();
        };
        credentialProvider = new CredentialProvider(secretResolver);
    }

    @Test
    @DisplayName("Resolves password from secret reference when provided")
    void testResolvePasswordFromSecretRef() {
        ConnectionDefinition def = new ConnectionDefinition();
        def.setConnectionId("C1");
        def.setAuthenticationType(AuthenticationType.PASSWORD);
        def.setPasswordSecretRef("KNOWN_SECRET");

        String password = credentialProvider.resolvePassword(def);
        assertThat(password).isEqualTo("resolved_secret_value");
    }

    @Test
    @DisplayName("Resolves password from direct password property")
    void testResolvePasswordFromDirectPassword() {
        ConnectionDefinition def = new ConnectionDefinition();
        def.setConnectionId("C1");
        def.setAuthenticationType(AuthenticationType.PASSWORD);
        def.setPassword("direct_pass");

        String password = credentialProvider.resolvePassword(def);
        assertThat(password).isEqualTo("direct_pass");
    }

    @Test
    @DisplayName("Throws when secret reference cannot be resolved")
    void testUnresolvedSecretThrows() {
        ConnectionDefinition def = new ConnectionDefinition();
        def.setConnectionId("C1");
        def.setAuthenticationType(AuthenticationType.PASSWORD);
        def.setPasswordSecretRef("UNKNOWN_SECRET");

        assertThatThrownBy(() -> credentialProvider.resolvePassword(def))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no password could be resolved");
    }

    @Test
    @DisplayName("Resolves private key from direct path")
    void testResolvePrivateKeyFromPath() {
        ConnectionDefinition def = new ConnectionDefinition();
        def.setConnectionId("C1");
        def.setAuthenticationType(AuthenticationType.PRIVATE_KEY);
        def.setPrivateKeyPath("C:/keys/id_rsa");

        String keyPath = credentialProvider.resolvePrivateKey(def);
        assertThat(keyPath).isEqualTo("C:/keys/id_rsa");
    }
}
