package com.mft.integration.checksum;

public enum ChecksumAlgorithm {
    SHA_256("SHA-256"),
    MD5("MD5");

    private final String algorithmName;

    ChecksumAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }
}
