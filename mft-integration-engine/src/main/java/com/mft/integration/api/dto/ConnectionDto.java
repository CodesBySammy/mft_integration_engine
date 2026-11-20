package com.mft.integration.api.dto;

import com.mft.integration.connection.AuthenticationType;
import com.mft.integration.connection.ConnectionDefinition;
import com.mft.integration.connection.HostKeyVerificationMode;

/**
 * Sanitized connection details DTO. Credentials are never exposed.
 */
public class ConnectionDto {

    private final String connectionId;
    private final String connectionType;
    private final String host;
    private final int port;
    private final String username;
    private final String remoteBaseDirectory;
    private final AuthenticationType authenticationType;
    private final HostKeyVerificationMode hostKeyVerificationMode;
    private final int connectionTimeoutMs;
    private final int sessionTimeoutMs;
    private final boolean enabled;

    public ConnectionDto(ConnectionDefinition conn) {
        this.connectionId = conn.getConnectionId();
        this.connectionType = conn.getConnectionType();
        this.host = conn.getHost();
        this.port = conn.getPort();
        this.username = conn.getUsername();
        this.remoteBaseDirectory = conn.getRemoteBaseDirectory();
        this.authenticationType = conn.getAuthenticationType();
        this.hostKeyVerificationMode = conn.getHostKeyVerificationMode();
        this.connectionTimeoutMs = conn.getConnectionTimeoutMs();
        this.sessionTimeoutMs = conn.getSessionTimeoutMs();
        this.enabled = conn.isEnabled();
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getConnectionType() {
        return connectionType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getRemoteBaseDirectory() {
        return remoteBaseDirectory;
    }

    public AuthenticationType getAuthenticationType() {
        return authenticationType;
    }

    public HostKeyVerificationMode getHostKeyVerificationMode() {
        return hostKeyVerificationMode;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
