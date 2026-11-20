package com.mft.integration.checksum;

import com.mft.integration.exception.IntegrityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChecksumServiceTest {

    private ChecksumService checksumService;

    @BeforeEach
    void setUp() {
        checksumService = new ChecksumService();
    }

    @Test
    @DisplayName("Calculates accurate SHA-256 for local file")
    void testCalculateChecksum(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "Hello, MFT Engine!");

        String sha256 = checksumService.calculateChecksum(file, ChecksumAlgorithm.SHA_256);
        assertThat(sha256).isNotNull().hasSize(64);

        // Idempotency: recalculating produces exact same checksum
        assertThat(checksumService.calculateChecksum(file, ChecksumAlgorithm.SHA_256)).isEqualTo(sha256);
    }

    @Test
    @DisplayName("Verifies checksum successfully when match")
    void testVerifyChecksumMatch(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "MATCHING_DATA");

        String actual = checksumService.calculateChecksum(file, ChecksumAlgorithm.SHA_256);
        ChecksumResult result = checksumService.verifyChecksum(file, actual, ChecksumAlgorithm.SHA_256);

        assertThat(result.isMatches()).isTrue();
        assertThat(result.getCalculatedChecksum()).isEqualTo(actual);
    }

    @Test
    @DisplayName("Throws IntegrityException when checksum mismatch occurs")
    void testVerifyChecksumMismatchThrows(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "VALID_DATA");

        String fakeChecksum = "0000000000000000000000000000000000000000000000000000000000000000";

        assertThatThrownBy(() -> checksumService.verifyChecksum(file, fakeChecksum, ChecksumAlgorithm.SHA_256))
                .isInstanceOf(IntegrityException.class)
                .hasMessageContaining("Checksum mismatch");
    }

    @Test
    @DisplayName("Parses standard companion checksum files correctly")
    void testParseCompanionFile(@TempDir Path tempDir) throws IOException {
        String expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        // 1. Standard GNU text format
        Path companion1 = tempDir.resolve("file1.txt.sha256");
        Files.writeString(companion1, expectedHash + "  file1.txt\n");
        assertThat(checksumService.parseCompanionChecksumFile(companion1)).isEqualTo(expectedHash);

        // 2. Binary asterisk format
        Path companion2 = tempDir.resolve("file2.txt.sha256");
        Files.writeString(companion2, expectedHash + " *file2.txt");
        assertThat(checksumService.parseCompanionChecksumFile(companion2)).isEqualTo(expectedHash);

        // 3. Bare hex digest format
        Path companion3 = tempDir.resolve("file3.txt.sha256");
        Files.writeString(companion3, expectedHash);
        assertThat(checksumService.parseCompanionChecksumFile(companion3)).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("Generates companion checksum file next to target")
    void testWriteCompanionFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("data.txt");
        Files.writeString(file, "PAYLOAD");

        String hash = checksumService.calculateChecksum(file, ChecksumAlgorithm.SHA_256);
        Path companion = checksumService.writeCompanionChecksumFile(file, hash, ChecksumAlgorithm.SHA_256);

        assertThat(Files.exists(companion)).isTrue();
        assertThat(companion.getFileName().toString()).isEqualTo("data.txt.sha256");
        assertThat(Files.readString(companion)).contains(hash);
    }
}
