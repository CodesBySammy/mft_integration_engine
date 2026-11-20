package com.mft.integration.connection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Encapsulates the immutable configuration for a single SFTP connection endpoint.
 * Sensitive fields (passwords, private keys) are never exposed via toString().
 */
public class ConnectionDefinition {

    @NotBlank(message = "connectionId must not be blank")
    private String connectionId;

    private String connectionType = "SFTP";

    @NotBlank(message = "host must not be blank")
    private String host;

    @Min(value = 1, message = "port must be between 1 and 65535")
    @Max(value = 65535, message = "port must be between 1 and 65535")
    private int port = 22;

    @NotBlank(message = "username must not be blank")
    private String username;

    @NotNull(message = "authenticationType must not be null")
    private AuthenticationType authenticationType = AuthenticationType.PASSWORD;

    /**
     * Direct password value (e.g. injected via environment variable expression in application.yml)
     */
    private String password;

    /**
     * Name of an environment variable or secret vault key holding the password
     */
    private String passwordSecretRef;

    /**
     * Local path to private key PEM file
     */
    private String privateKeyPath;

    /**
     * Name of an environment variable or secret vault key holding the private key
     */
    private String privateKeySecretRef;

    /**
     * Passphrase for encrypted private key if applicable
     */
    private String privateKeyPassphrase;

    private String remoteBaseDirectory = "/";

    private int connectionTimeoutMs = 15000;

    private int sessionTimeoutMs = 30000;

    private HostKeyVerificationMode hostKeyVerificationMode = HostKeyVerificationMode.KNOWN_HOSTS;

    private String knownHostsFile;

    private boolean enabled = true;

    public ConnectionDefinition() {
    }

    public ConnectionDefinition(String connectionId, String host, int port, String username,
                                AuthenticationType authenticationType, String password, String remoteBaseDirectory) {
        this.connectionId = connectionId;
        this.host = host;
        this.port = port;
        this.username = username;
        this.authenticationType = authenticationType;
        this.password = password;
        this.remoteBaseDirectory = remoteBaseDirectory;
    }

    // Getters and Setters

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public AuthenticationType getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(AuthenticationType authenticationType) {
        this.authenticationType = authenticationType;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordSecretRef() {
        return passwordSecretRef;
    }

    public void setPasswordSecretRef(String passwordSecretRef) {
        this.passwordSecretRef = passwordSecretRef;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPrivateKeySecretRef() {
        return privateKeySecretRef;
    }

    public void setPrivateKeySecretRef(String privateKeySecretRef) {
        this.privateKeySecretRef = privateKeySecretRef;
    }

    public String getPrivateKeyPassphrase() {
        return privateKeyPassphrase;
    }

    public void setPrivateKeyPassphrase(String privateKeyPassphrase) {
        this.privateKeyPassphrase = privateKeyPassphrase;
    }

    public String getRemoteBaseDirectory() {
        return remoteBaseDirectory;
    }

    public void setRemoteBaseDirectory(String remoteBaseDirectory) {
        this.remoteBaseDirectory = remoteBaseDirectory;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public void setSessionTimeoutMs(int sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public HostKeyVerificationMode getHostKeyVerificationMode() {
        return hostKeyVerificationMode;
    }

    public void setHostKeyVerificationMode(HostKeyVerificationMode hostKeyVerificationMode) {
        this.hostKeyVerificationMode = hostKeyVerificationMode;
    }

    public String getKnownHostsFile() {
        return knownHostsFile;
    }

    public void setKnownHostsFile(String knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionDefinition that = (ConnectionDefinition) o;
        return Objects.equals(connectionId, that.connectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId);
    }

    @Override
    public String toString() {
        return "ConnectionDefinition{" +
                "connectionId='" + connectionId + '\'' +
                ", connectionType='" + connectionType + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", authenticationType=" + authenticationType +
                ", password='[MASKED]'" +
                ", passwordSecretRef='" + passwordSecretRef + '\'' +
                ", privateKeyPath='" + privateKeyPath + '\'' +
                ", privateKeySecretRef='" + privateKeySecretRef + '\'' +
                ", remoteBaseDirectory='" + remoteBaseDirectory + '\'' +
                ", connectionTimeoutMs=" + connectionTimeoutMs +
                ", sessionTimeoutMs=" + sessionTimeoutMs +
                ", hostKeyVerificationMode=" + hostKeyVerificationMode +
                ", knownHostsFile='" + knownHostsFile + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
