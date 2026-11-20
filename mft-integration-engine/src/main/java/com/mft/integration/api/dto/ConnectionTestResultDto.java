package com.mft.integration.api.dto;

/**
 * Result of testing connectivity to a configured SFTP endpoint.
 */
public class ConnectionTestResultDto {

    private final String connectionId;
    private final String host;
    private final int port;
    private final boolean reachable;
    private final long responseTimeMs;
    private final String message;

    public ConnectionTestResultDto(String connectionId, String host, int port, boolean reachable, long responseTimeMs, String message) {
        this.connectionId = connectionId;
        this.host = host;
        this.port = port;
        this.reachable = reachable;
        this.responseTimeMs = responseTimeMs;
        this.message = message;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isReachable() {
        return reachable;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public String getMessage() {
        return message;
    }
}
