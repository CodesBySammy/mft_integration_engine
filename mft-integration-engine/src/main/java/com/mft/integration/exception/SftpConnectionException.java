package com.mft.integration.exception;

public class SftpConnectionException extends MftException {

    private final String connectionId;

    public SftpConnectionException(String connectionId, String message) {
        super("SFTP_CONNECTION_ERROR", "Connection [" + connectionId + "] failed: " + message);
        this.connectionId = connectionId;
    }

    public SftpConnectionException(String connectionId, String message, Throwable cause) {
        super("SFTP_CONNECTION_ERROR", "Connection [" + connectionId + "] failed: " + message, cause);
        this.connectionId = connectionId;
    }

    public String getConnectionId() {
        return connectionId;
    }
}
