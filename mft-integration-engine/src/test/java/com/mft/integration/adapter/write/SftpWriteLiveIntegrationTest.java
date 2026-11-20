package com.mft.integration.adapter.write;

import com.mft.integration.adapter.model.RemoteFileInfo;
import com.mft.integration.adapter.model.TransferResult;
import com.mft.integration.adapter.read.ReadAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class SftpWriteLiveIntegrationTest {

    @Autowired
    private WriteAdapter writeAdapter;

    @Autowired
    private ReadAdapter readAdapter;

    @Test
    @DisplayName("Live test: Upload and publish file atomically on real Destination SFTPGo (Server B on port 2222)")
    void testLivePublish(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("SL_RECON_20261120_RUN001.txt");
        String payload = "HDR|20261120|001\nTRD001|USD|1500.50|SUCCESS\nTRD002|EUR|2800.75|SUCCESS\nTRL|2|4301.25";
        Files.writeString(sourceFile, payload);

        // Perform two-phase publication: uploads to .part then renames to target
        TransferResult result = writeAdapter.publishFile(
                "C_FILE_OUT",
                sourceFile,
                "/incoming",
                "SL_RECON_20261120_RUN001.txt",
                true
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBytesTransferred()).isEqualTo(payload.getBytes().length);

        // Verify remote existence on Server B
        boolean exists = writeAdapter.remoteFileExists("C_FILE_OUT", "/incoming/SL_RECON_20261120_RUN001.txt");
        assertThat(exists).isTrue();

        // Download the published file back from Server B using ReadAdapter to verify byte fidelity
        Path downloadedFile = tempDir.resolve("verified_from_server_b.txt");
        readAdapter.downloadFile("C_FILE_OUT", "/incoming/SL_RECON_20261120_RUN001.txt", downloadedFile, true);

        assertThat(Files.exists(downloadedFile)).isTrue();
        assertThat(Files.readString(downloadedFile)).isEqualTo(payload);

        // Verify metadata on Server B
        Optional<RemoteFileInfo> metadata = readAdapter.getFileMetadata("C_FILE_OUT", "/incoming/SL_RECON_20261120_RUN001.txt");
        assertThat(metadata).isPresent();
        assertThat(metadata.get().getSize()).isEqualTo(payload.getBytes().length);
    }
}
