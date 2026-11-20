package com.mft.integration.adapter.read;

import com.mft.integration.adapter.model.RemoteFileInfo;
import com.mft.integration.config.SftpSessionFactoryRegistry;
import com.mft.integration.exception.MftException;
import com.mft.integration.exception.SftpConnectionException;
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SftpReadAdapterTest {

    private SftpSessionFactoryRegistry registry;
    private SessionFactory<SftpClient.DirEntry> sessionFactory;
    private Session<SftpClient.DirEntry> session;
    private SftpReadAdapter readAdapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = Mockito.mock(SftpSessionFactoryRegistry.class);
        sessionFactory = Mockito.mock(SessionFactory.class);
        session = Mockito.mock(Session.class);

        when(registry.getSessionFactory("C_TEST")).thenReturn(sessionFactory);
        when(sessionFactory.getSession()).thenReturn(session);

        readAdapter = new SftpReadAdapter(registry);
    }

    private SftpClient.DirEntry createMockDirEntry(String filename, boolean isDir, long size) {
        SftpClient.Attributes attrs = Mockito.mock(SftpClient.Attributes.class);
        when(attrs.isDirectory()).thenReturn(isDir);
        when(attrs.getSize()).thenReturn(size);

        return new SftpClient.DirEntry(filename, filename, attrs);
    }

    @Test
    @DisplayName("testConnection returns true when session is open")
    void testConnectionSuccess() {
        when(session.isOpen()).thenReturn(true);
        boolean result = readAdapter.testConnection("C_TEST");
        assertThat(result).isTrue();
        verify(session).close();
    }

    @Test
    @DisplayName("testConnection throws SftpConnectionException on session failure")
    void testConnectionFailure() {
        when(sessionFactory.getSession()).thenThrow(new RuntimeException("Connection refused"));
        assertThatThrownBy(() -> readAdapter.testConnection("C_TEST"))
                .isInstanceOf(SftpConnectionException.class)
                .hasMessageContaining("SFTP handshake failed");
    }

    @Test
    @DisplayName("listFiles returns remote files and filters out '.' and '..'")
    void testListFilesFiltersDots() throws IOException {
        SftpClient.DirEntry dot = createMockDirEntry(".", true, 0);
        SftpClient.DirEntry dotDot = createMockDirEntry("..", true, 0);
        SftpClient.DirEntry file1 = createMockDirEntry("trades.txt", false, 1024);
        SftpClient.DirEntry subDir = createMockDirEntry("sub", true, 0);

        when(session.list("/upload")).thenReturn(new SftpClient.DirEntry[]{dot, dotDot, file1, subDir});

        List<RemoteFileInfo> results = readAdapter.listFiles("C_TEST", "/upload");
        assertThat(results).hasSize(2);
        assertThat(results).extracting(RemoteFileInfo::getFilename).containsExactlyInAnyOrder("trades.txt", "sub");
    }

    @Test
    @DisplayName("discoverFiles filters out subdirectories and temporary .part files")
    void testDiscoverFilesFiltersTempAndDirs() throws IOException {
        SftpClient.DirEntry validFile = createMockDirEntry("trades_01.txt", false, 2048);
        SftpClient.DirEntry nonMatching = createMockDirEntry("trades_01.dat", false, 2048);
        SftpClient.DirEntry partFile = createMockDirEntry("trades_02.txt.part", false, 1024);
        SftpClient.DirEntry dir = createMockDirEntry("incoming", true, 0);

        when(session.list("/upload")).thenReturn(new SftpClient.DirEntry[]{validFile, nonMatching, partFile, dir});

        List<RemoteFileInfo> candidates = readAdapter.discoverFiles("C_TEST", "/upload", "*.txt");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getFilename()).isEqualTo("trades_01.txt");
    }

    @Test
    @DisplayName("fileExists delegates to session")
    void testFileExists() throws IOException {
        when(session.exists("/upload/test.txt")).thenReturn(true);
        when(session.exists("/upload/missing.txt")).thenReturn(false);

        assertThat(readAdapter.fileExists("C_TEST", "/upload/test.txt")).isTrue();
        assertThat(readAdapter.fileExists("C_TEST", "/upload/missing.txt")).isFalse();
    }

    @Test
    @DisplayName("downloadFile writes file and respects overwrite protection")
    void testDownloadFile(@TempDir Path tempDir) throws IOException {
        Path destination = tempDir.resolve("local_trades.txt");
        when(session.exists("/upload/trades.txt")).thenReturn(true);

        doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(1);
            out.write("TRADE,100,USD".getBytes());
            return null;
        }).when(session).read(eq("/upload/trades.txt"), any(OutputStream.class));

        Path downloaded = readAdapter.downloadFile("C_TEST", "/upload/trades.txt", destination, false);

        assertThat(Files.exists(downloaded)).isTrue();
        assertThat(Files.readString(downloaded)).isEqualTo("TRADE,100,USD");

        // Attempting to download again without overwrite should fail
        assertThatThrownBy(() -> readAdapter.downloadFile("C_TEST", "/upload/trades.txt", destination, false))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("overwrite is disabled");

        // With overwrite=true it should succeed
        Path redownloaded = readAdapter.downloadFile("C_TEST", "/upload/trades.txt", destination, true);
        assertThat(redownloaded).isEqualTo(destination);
    }
}
