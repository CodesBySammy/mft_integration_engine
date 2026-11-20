package com.mft.integration.api;

import com.mft.integration.adapter.model.RemoteFileInfo;
import com.mft.integration.adapter.read.ReadAdapter;
import com.mft.integration.api.dto.ApiResponse;
import com.mft.integration.api.dto.RemoteFileDto;
import com.mft.integration.workspace.LocalWorkspaceService;
import com.mft.integration.workspace.WorkspaceDirectory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST controller for remote file discovery and manual file download triggers.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final ReadAdapter readAdapter;
    private final LocalWorkspaceService workspaceService;

    public FileController(ReadAdapter readAdapter, LocalWorkspaceService workspaceService) {
        this.readAdapter = readAdapter;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/{connectionId}")
    public ResponseEntity<ApiResponse<List<RemoteFileDto>>> listRemoteFiles(
            @PathVariable String connectionId,
            @RequestParam(defaultValue = "/") String directory,
            @RequestParam(defaultValue = "*") String pattern) {

        List<RemoteFileInfo> remoteFiles = readAdapter.discoverFiles(connectionId, directory, pattern);
        List<RemoteFileDto> dtos = remoteFiles.stream()
                .map(RemoteFileDto::new)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PostMapping("/download")
    public ResponseEntity<ApiResponse<Map<String, Object>>> downloadFile(
            @RequestParam String connectionId,
            @RequestParam String remotePath,
            @RequestParam(required = false) String targetFilename) {

        String filename = (targetFilename != null && !targetFilename.isBlank())
                ? targetFilename
                : Path.of(remotePath).getFileName().toString();

        Path localTarget = workspaceService.resolvePath(WorkspaceDirectory.DOWNLOADS, filename);
        Path downloaded = readAdapter.downloadFile(connectionId, remotePath, localTarget, true);

        return ResponseEntity.ok(ApiResponse.ok("File downloaded successfully", Map.of(
                "connectionId", connectionId,
                "remotePath", remotePath,
                "localPath", downloaded.toString(),
                "filename", filename
        )));
    }
}
