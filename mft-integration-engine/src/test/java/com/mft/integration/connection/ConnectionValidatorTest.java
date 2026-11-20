package com.mft.integration.connection;

import com.mft.integration.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionValidatorTest {

    private ConnectionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConnectionValidator();
    }

    private ConnectionDefinition createValidPasswordConnection() {
        ConnectionDefinition def = new ConnectionDefinition();
        def.setConnectionId("C_FILE_IN");
        def.setHost("localhost");
        def.setPort(2022);
        def.setUsername("mft_source");
        def.setAuthenticationType(AuthenticationType.PASSWORD);
        def.setPassword("secret123");
        def.setRemoteBaseDirectory("/");
        def.setConnectionTimeoutMs(10000);
        def.setSessionTimeoutMs(30000);
        def.setHostKeyVerificationMode(HostKeyVerificationMode.ACCEPT_ALL_DEV_ONLY);
        return def;
    }

    @Test
    @DisplayName("Valid password-based connection passes validation")
    void testValidPasswordConnection() {
        ConnectionDefinition def = createValidPasswordConnection();
        assertThatCode(() -> validator.validate(def)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valid private-key connection passes validation")
    void testValidPrivateKeyConnection() {
        ConnectionDefinition def = createValidPasswordConnection();
        def.setAuthenticationType(AuthenticationType.PRIVATE_KEY);
        def.setPassword(null);
        def.setPrivateKeySecretRef("SFTP_PRIVATE_KEY");
        assertThatCode(() -> validator.validate(def)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Null connection definition throws ValidationException")
    void testNullConnection() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid connection", "conn$#@", "c/in"})
    @DisplayName("Invalid connectionId throws ValidationException")
    void testInvalidConnectionId(String invalidId) {
        ConnectionDefinition def = createValidPasswordConnection();
        def.setConnectionId(invalidId);
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Blank host throws ValidationException")
    void testBlankHost() {
        ConnectionDefinition def = createValidPasswordConnection();
        def.setHost("   ");
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("host must not be blank");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65536, 100000})
    @DisplayName("Invalid port number throws ValidationException")
    void testInvalidPort(int port) {
        ConnectionDefinition def = createValidPasswordConnection();
        def.setPort(port);
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("port must be between 1 and 65535");
    }

    @Test
    @DisplayName("Blank username throws ValidationException")
    void testBlankUsername() {
        ConnectionDefinition def = createValidPasswordConnection();
        def.setUsername("");
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("username must not be blank");
    }

    @Test
    @DisplayName("Password authentication without password or ref throws ValidationException")
    void testMissingPasswordCredentials() {
        ConnectionDefinition def = createValidPasswordConnection();
        def.setPassword(null);
        def.setPasswordSecretRef(null);
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("requires either 'password' or 'passwordSecretRef'");
    }

    @Test
    @DisplayName("Private key authentication without key or ref throws ValidationException")
    void testMissingPrivateKeyCredentials() {
        ConnectionDefinition def = createValidPasswordConnection();
        def.setAuthenticationType(AuthenticationType.PRIVATE_KEY);
        def.setPassword(null);
        def.setPrivateKeyPath(null);
        def.setPrivateKeySecretRef(null);
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("requires either 'privateKeyPath' or 'privateKeySecretRef'");
    }

    @Test
    @DisplayName("Path traversal in remoteBaseDirectory throws ValidationException")
    void testPathTraversalInRemoteBaseDirectory() {
        ConnectionDefinition def = createValidPasswordConnection();
        def.setRemoteBaseDirectory("/../../etc");
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    @DisplayName("Negative timeouts throw ValidationException")
    void testNegativeTimeouts() {
        ConnectionDefinition def = createValidPasswordConnection();
        def.setConnectionTimeoutMs(-1);
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("connectionTimeoutMs must be positive");
    }

    @Test
    @DisplayName("toString masks password")
    void testToStringMasksPassword() {
        ConnectionDefinition def = createValidPasswordConnection();
        assertThat(def.toString()).doesNotContain("secret123");
        assertThat(def.toString()).contains("[MASKED]");
    }
}
