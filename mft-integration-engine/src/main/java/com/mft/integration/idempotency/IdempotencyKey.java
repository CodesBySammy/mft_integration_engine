package com.mft.integration.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Deterministic idempotency key for identifying unique file transfers.
 * Composed of routeId, sourceConnectionId, sourceFilePath, fileSize, and sourceChecksum.
 */
public class IdempotencyKey {

    private final String routeId;
    private final String sourceConnectionId;
    private final String sourceFilePath;
    private final long fileSize;
    private final String sourceChecksum;
    private final String keyString;
    private final String hash;

    public IdempotencyKey(String routeId, String sourceConnectionId, String sourceFilePath, long fileSize, String sourceChecksum) {
        this.routeId = Objects.requireNonNull(routeId, "routeId must not be null");
        this.sourceConnectionId = Objects.requireNonNull(sourceConnectionId, "sourceConnectionId must not be null");
        this.sourceFilePath = Objects.requireNonNull(sourceFilePath, "sourceFilePath must not be null");
        this.fileSize = fileSize;
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum must not be null");

        this.keyString = String.format("%s:%s:%s:%d:%s", routeId, sourceConnectionId, sourceFilePath, fileSize, sourceChecksum);
        this.hash = computeSha256(this.keyString);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest algorithm not available", e);
        }
    }

    public String getRouteId() {
        return routeId;
    }

    public String getSourceConnectionId() {
        return sourceConnectionId;
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getSourceChecksum() {
        return sourceChecksum;
    }

    public String getKeyString() {
        return keyString;
    }

    public String getHash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyKey that = (IdempotencyKey) o;
        return hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public String toString() {
        return "IdempotencyKey{" +
                "hash='" + hash + '\'' +
                ", keyString='" + keyString + '\'' +
                '}';
    }
}
