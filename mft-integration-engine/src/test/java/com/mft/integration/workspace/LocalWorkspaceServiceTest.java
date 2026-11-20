package com.mft.integration.workspace;

import com.mft.integration.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalWorkspaceServiceTest {

    private LocalWorkspaceService workspaceService;
    private Path rootDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        rootDir = tempDir.resolve("test-workspace");
        workspaceService = new LocalWorkspaceService(rootDir.toString());
        workspaceService.initialize();
    }

    @Test
    @DisplayName("Initializes all required lifecycle directories")
    void testDirectoryInitialization() {
        for (WorkspaceDirectory dir : WorkspaceDirectory.values()) {
            Path subDir = workspaceService.getDirectory(dir);
            assertThat(Files.exists(subDir)).isTrue();
            assertThat(Files.isDirectory(subDir)).isTrue();
        }
    }

    @Test
    @DisplayName("Resolves path inside designated workspace directory")
    void testResolveValidPath() {
        Path target = workspaceService.resolvePath(WorkspaceDirectory.DOWNLOADS, "positions.txt");
        assertThat(target).isEqualTo(rootDir.resolve("downloads/positions.txt"));
    }

    @Test
    @DisplayName("Rejects path traversal escape attempts")
    void testRejectsPathTraversal() {
        assertThatThrownBy(() -> workspaceService.resolvePath(WorkspaceDirectory.DOWNLOADS, "../../escape.txt"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Quarantines file and generates reason record")
    void testQuarantineFile(@TempDir Path tempDir) throws IOException {
        Path badFile = tempDir.resolve("corrupted_trade.dat");
        Files.writeString(badFile, "CORRUPTED_BYTES");

        Path quarantined = workspaceService.quarantineFile(badFile, "RUN_001", "Checksum mismatch validation failure");

        assertThat(Files.exists(badFile)).isFalse();
        assertThat(Files.exists(quarantined)).isTrue();
        assertThat(quarantined.getParent().getFileName().toString()).isEqualTo("RUN_001");

        Path reasonFile = quarantined.resolveSibling("corrupted_trade.dat.reason.txt");
        assertThat(Files.exists(reasonFile)).isTrue();
        assertThat(Files.readString(reasonFile)).contains("Checksum mismatch");
    }
}
