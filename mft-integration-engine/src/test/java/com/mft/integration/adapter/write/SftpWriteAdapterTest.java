package com.mft.integration.adapter.write;

import com.mft.integration.adapter.model.TransferResult;
import com.mft.integration.config.SftpSessionFactoryRegistry;
import com.mft.integration.exception.MftException;
import com.mft.integration.exception.ValidationException;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SftpWriteAdapterTest {

    private SftpSessionFactoryRegistry registry;
    private SessionFactory<SftpClient.DirEntry> sessionFactory;
    private Session<SftpClient.DirEntry> session;
    private SftpWriteAdapter writeAdapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = Mockito.mock(SftpSessionFactoryRegistry.class);
        sessionFactory = Mockito.mock(SessionFactory.class);
        session = Mockito.mock(Session.class);

        when(registry.getSessionFactory("C_TEST_OUT")).thenReturn(sessionFactory);
        when(sessionFactory.getSession()).thenReturn(session);

        writeAdapter = new SftpWriteAdapter(registry);
    }

    @Test
    @DisplayName("uploadFile writes stream to remote file and returns success")
    void testUploadFile(@TempDir Path tempDir) throws IOException {
        Path localFile = tempDir.resolve("payload.txt");
        Files.writeString(localFile, "SAMPLE DATA PAYLOAD");

        when(session.exists("/incoming/payload.txt")).thenReturn(false);
        when(session.exists("/incoming")).thenReturn(true);

        TransferResult result = writeAdapter.uploadFile("C_TEST_OUT", localFile, "/incoming/payload.txt", false);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBytesTransferred()).isEqualTo(19);
        verify(session).write(any(InputStream.class), eq("/incoming/payload.txt"));
    }

    @Test
    @DisplayName("uploadFile rejects overwrite when overwrite=false")
    void testUploadFileRejectsOverwrite(@TempDir Path tempDir) throws IOException {
        Path localFile = tempDir.resolve("payload.txt");
        Files.writeString(localFile, "DATA");

        when(session.exists("/incoming/payload.txt")).thenReturn(true);

        assertThatThrownBy(() -> writeAdapter.uploadFile("C_TEST_OUT", localFile, "/incoming/payload.txt", false))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("overwrite is disabled");
    }

    @Test
    @DisplayName("publishFile uploads with .part extension then atomically renames to target")
    void testPublishFile(@TempDir Path tempDir) throws IOException {
        Path localFile = tempDir.resolve("final_report.csv");
        Files.writeString(localFile, "COL1,COL2\nA,B");

        when(session.exists("/reports/final_report.csv")).thenReturn(false);
        when(session.exists("/reports")).thenReturn(true);

        TransferResult result = writeAdapter.publishFile("C_TEST_OUT", localFile, "/reports", "final_report.csv", false);

        assertThat(result.isSuccess()).isTrue();
        // Verifies upload to staging .part
        verify(session).write(any(InputStream.class), eq("/reports/final_report.csv.part"));
        // Verifies rename from .part to final target
        verify(session).rename("/reports/final_report.csv.part", "/reports/final_report.csv");
    }

    @Test
    @DisplayName("renameRemoteFile renames source to destination")
    void testRenameSuccess() throws IOException {
        when(session.exists("/source.txt")).thenReturn(true);

        writeAdapter.renameRemoteFile("C_TEST_OUT", "/source.txt", "/dest.txt");
        verify(session).rename("/source.txt", "/dest.txt");
    }

    @Test
    @DisplayName("renameRemoteFile throws when source file does not exist")
    void testRenameSourceNotFound() throws IOException {
        when(session.exists("/missing.txt")).thenReturn(false);

        assertThatThrownBy(() -> writeAdapter.renameRemoteFile("C_TEST_OUT", "/missing.txt", "/dest.txt"))
                .isInstanceOf(MftException.class)
                .hasMessageContaining("remote source file does not exist");
    }
}
