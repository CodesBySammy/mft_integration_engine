package com.mft.integration.checksum;

import java.time.Instant;
import java.util.Objects;

/**
 * Result of a checksum computation or verification.
 */
public class ChecksumResult {

    private final String expectedChecksum;
    private final String calculatedChecksum;
    private final ChecksumAlgorithm algorithm;
    private final boolean matches;
    private final Instant timestamp;

    public ChecksumResult(String expectedChecksum, String calculatedChecksum, ChecksumAlgorithm algorithm, boolean matches) {
        this.expectedChecksum = expectedChecksum;
        this.calculatedChecksum = calculatedChecksum;
        this.algorithm = algorithm;
        this.matches = matches;
        this.timestamp = Instant.now();
    }

    public static ChecksumResult computed(String calculatedChecksum, ChecksumAlgorithm algorithm) {
        return new ChecksumResult(null, calculatedChecksum, algorithm, true);
    }

    public static ChecksumResult verified(String expectedChecksum, String calculatedChecksum, ChecksumAlgorithm algorithm) {
        boolean match = expectedChecksum != null &&
                expectedChecksum.trim().equalsIgnoreCase(calculatedChecksum != null ? calculatedChecksum.trim() : "");
        return new ChecksumResult(expectedChecksum, calculatedChecksum, algorithm, match);
    }

    public String getExpectedChecksum() {
        return expectedChecksum;
    }

    public String getCalculatedChecksum() {
        return calculatedChecksum;
    }

    public ChecksumAlgorithm getAlgorithm() {
        return algorithm;
    }

    public boolean isMatches() {
        return matches;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChecksumResult that = (ChecksumResult) o;
        return matches == that.matches &&
                Objects.equals(expectedChecksum, that.expectedChecksum) &&
                Objects.equals(calculatedChecksum, that.calculatedChecksum) &&
                algorithm == that.algorithm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(expectedChecksum, calculatedChecksum, algorithm, matches);
    }

    @Override
    public String toString() {
        return "ChecksumResult{" +
                "expectedChecksum='" + expectedChecksum + '\'' +
                ", calculatedChecksum='" + calculatedChecksum + '\'' +
                ", algorithm=" + algorithm +
                ", matches=" + matches +
                ", timestamp=" + timestamp +
                '}';
    }
}
