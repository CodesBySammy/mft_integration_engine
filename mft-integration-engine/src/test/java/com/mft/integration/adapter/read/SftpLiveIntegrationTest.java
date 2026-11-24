package com.mft.integration.adapter.read;

import com.mft.integration.adapter.model.RemoteFileInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class SftpLiveIntegrationTest {

    @Autowired
    private ReadAdapter readAdapter;

    @org.junit.jupiter.api.BeforeEach
    void ensurePositionsFile() throws IOException {
        Path uploadDir = Path.of("D:\\sftpgo\\instance-a\\data\\mft_source\\upload");
        if (Files.exists(uploadDir)) {
            Path pos = uploadDir.resolve("positions.txt");
            if (!Files.exists(pos) || Files.size(pos) == 0) {
                Files.writeString(pos, "TRD000000001USD000000000001500.5020261120\n".repeat(30));
            }
        }
    }

    @Test
    @DisplayName("Live test: Test connection to Source SFTPGo (Server A on port 2022)")
    void testLiveSourceConnection() {
        boolean connected = readAdapter.testConnection("C_FILE_IN");
        assertThat(connected).isTrue();
    }

    @Test
    @DisplayName("Live test: Test connection to Destination SFTPGo (Server B on port 2222)")
    void testLiveDestinationConnection() {
        boolean connected = readAdapter.testConnection("C_FILE_OUT");
        assertThat(connected).isTrue();
    }

    @Test
    @DisplayName("Live test: List files and discover positions.txt on Source SFTPGo")
    void testLiveFileDiscovery() {
        List<RemoteFileInfo> files = readAdapter.listFiles("C_FILE_IN", "/");
        assertThat(files).isNotEmpty();

        // Discover *.txt files
        List<RemoteFileInfo> txtFiles = readAdapter.discoverFiles("C_FILE_IN", "/upload", "*.txt");
        assertThat(txtFiles).isNotEmpty();
        assertThat(txtFiles).anyMatch(f -> f.getFilename().equals("positions.txt") && f.getSize() > 0);
    }

    @Test
    @DisplayName("Live test: Download positions.txt from real Source SFTPGo")
    void testLiveFileDownload(@TempDir Path tempDir) throws IOException {
        Path localTarget = tempDir.resolve("downloaded_positions.txt");

        Path downloaded = readAdapter.downloadFile("C_FILE_IN", "/upload/positions.txt", localTarget, true);

        assertThat(Files.exists(downloaded)).isTrue();
        List<String> lines = Files.readAllLines(downloaded);
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).startsWith("TRD000000001USD");
    }
}
