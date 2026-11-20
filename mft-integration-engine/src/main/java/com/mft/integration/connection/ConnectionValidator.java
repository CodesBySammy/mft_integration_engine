package com.mft.integration.connection;

import com.mft.integration.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Validates connection definitions at application startup or registration time.
 * Enforces fail-fast behavior for malformed or insecure configurations.
 */
@Component
public class ConnectionValidator {

    private static final Logger log = LoggerFactory.getLogger(ConnectionValidator.class);

    public void validate(ConnectionDefinition conn) {
        if (conn == null) {
            throw new ValidationException("CONNECTION_NULL", "Connection definition must not be null.");
        }

        String id = conn.getConnectionId();
        if (id == null || id.isBlank()) {
            throw new ValidationException("INVALID_CONNECTION_ID", "Connection ID must not be blank.");
        }

        if (!id.matches("^[a-zA-Z0-9_-]+$")) {
            throw new ValidationException("INVALID_CONNECTION_ID",
                    "Connection ID [" + id + "] contains illegal characters. Only alphanumeric, '_' and '-' are permitted.");
        }

        if (conn.getHost() == null || conn.getHost().isBlank()) {
            throw new ValidationException("INVALID_HOST", "Connection [" + id + "] host must not be blank.");
        }

        if (conn.getPort() < 1 || conn.getPort() > 65535) {
            throw new ValidationException("INVALID_PORT",
                    "Connection [" + id + "] port must be between 1 and 65535. Given: " + conn.getPort());
        }

        if (conn.getUsername() == null || conn.getUsername().isBlank()) {
            throw new ValidationException("INVALID_USERNAME", "Connection [" + id + "] username must not be blank.");
        }

        if (conn.getAuthenticationType() == null) {
            throw new ValidationException("INVALID_AUTH_TYPE", "Connection [" + id + "] authenticationType must not be null.");
        }

        // Validate Authentication Credentials
        if (conn.getAuthenticationType() == AuthenticationType.PASSWORD) {
            boolean hasPassword = conn.getPassword() != null && !conn.getPassword().isBlank();
            boolean hasPasswordRef = conn.getPasswordSecretRef() != null && !conn.getPasswordSecretRef().isBlank();
            if (!hasPassword && !hasPasswordRef) {
                throw new ValidationException("MISSING_CREDENTIALS",
                        "Connection [" + id + "] requires either 'password' or 'passwordSecretRef' for PASSWORD authentication.");
            }
        } else if (conn.getAuthenticationType() == AuthenticationType.PRIVATE_KEY) {
            boolean hasKeyPath = conn.getPrivateKeyPath() != null && !conn.getPrivateKeyPath().isBlank();
            boolean hasKeyRef = conn.getPrivateKeySecretRef() != null && !conn.getPrivateKeySecretRef().isBlank();
            if (!hasKeyPath && !hasKeyRef) {
                throw new ValidationException("MISSING_CREDENTIALS",
                        "Connection [" + id + "] requires either 'privateKeyPath' or 'privateKeySecretRef' for PRIVATE_KEY authentication.");
            }
            if (hasKeyPath) {
                Path keyFile = Paths.get(conn.getPrivateKeyPath());
                if (!Files.exists(keyFile)) {
                    log.warn("Connection [{}] private key file does not exist at path: {}", id, conn.getPrivateKeyPath());
                }
            }
        }

        // Validate Remote Base Directory
        String baseDir = conn.getRemoteBaseDirectory();
        if (baseDir == null || baseDir.isBlank()) {
            conn.setRemoteBaseDirectory("/");
        } else {
            if (baseDir.contains("..")) {
                throw new ValidationException("PATH_TRAVERSAL_DETECTED",
                        "Connection [" + id + "] remoteBaseDirectory contains path traversal '..': " + baseDir);
            }
        }

        // Validate Timeouts
        if (conn.getConnectionTimeoutMs() <= 0) {
            throw new ValidationException("INVALID_TIMEOUT",
                    "Connection [" + id + "] connectionTimeoutMs must be positive: " + conn.getConnectionTimeoutMs());
        }

        if (conn.getSessionTimeoutMs() <= 0) {
            throw new ValidationException("INVALID_TIMEOUT",
                    "Connection [" + id + "] sessionTimeoutMs must be positive: " + conn.getSessionTimeoutMs());
        }

        // Host Key Verification
        if (conn.getHostKeyVerificationMode() == HostKeyVerificationMode.ACCEPT_ALL_DEV_ONLY) {
            log.warn("SECURITY WARNING: Connection [{}] has hostKeyVerificationMode set to ACCEPT_ALL_DEV_ONLY. This setting is strictly for local testing and must NOT be used in production.", id);
        } else if (conn.getHostKeyVerificationMode() == HostKeyVerificationMode.KNOWN_HOSTS) {
            if (conn.getKnownHostsFile() != null && !conn.getKnownHostsFile().isBlank()) {
                Path knownHostsPath = Paths.get(conn.getKnownHostsFile());
                if (!Files.exists(knownHostsPath)) {
                    log.warn("Connection [{}] known_hosts file not found at: {}", id, conn.getKnownHostsFile());
                }
            }
        }

        log.debug("Connection definition [{}] validated successfully.", id);
    }
}
