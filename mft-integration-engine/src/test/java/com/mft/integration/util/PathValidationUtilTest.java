package com.mft.integration.util;

import com.mft.integration.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathValidationUtilTest {

    @Test
    @DisplayName("Normalizes valid remote paths")
    void testValidRemotePathNormalization() {
        assertThat(PathValidationUtil.validateAndNormalizeRemotePath("/upload/inbound/"))
                .isEqualTo("/upload/inbound/");
        assertThat(PathValidationUtil.validateAndNormalizeRemotePath("\\upload\\inbound\\file.txt"))
                .isEqualTo("/upload/inbound/file.txt");
        assertThat(PathValidationUtil.validateAndNormalizeRemotePath("//upload///inbound//"))
                .isEqualTo("/upload/inbound/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "/../etc/passwd", "upload/../../etc", "upload/..", "/upload/inbound/.."})
    @DisplayName("Path traversal attempts throw ValidationException")
    void testPathTraversalRejected(String traversalPath) {
        assertThatThrownBy(() -> PathValidationUtil.validateAndNormalizeRemotePath(traversalPath))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Path traversal");
    }

    @Test
    @DisplayName("Combines base directory and relative path safely")
    void testCombineRemotePath() {
        assertThat(PathValidationUtil.combineRemotePath("/base/dir", "file.txt"))
                .isEqualTo("/base/dir/file.txt");
        assertThat(PathValidationUtil.combineRemotePath("/base/dir/", "/sub/file.txt"))
                .isEqualTo("/base/dir/sub/file.txt");
    }

    @Test
    @DisplayName("Workspace path validation accepts files inside root and rejects escapes")
    void testWorkspacePathValidation(@TempDir Path tempDir) {
        Path allowedBase = tempDir.resolve("workspace");
        Path insidePath = allowedBase.resolve("downloads/file.txt");
        Path outsidePath = tempDir.resolve("outside_system_file.txt");

        Path validated = PathValidationUtil.validateLocalWorkspacePath(insidePath, allowedBase);
        assertThat(validated).isEqualTo(insidePath.toAbsolutePath().normalize());

        assertThatThrownBy(() -> PathValidationUtil.validateLocalWorkspacePath(outsidePath, allowedBase))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("outside allowed workspace");
    }
}
