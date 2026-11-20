# Read Adapter Architecture & Implementation Guide

## 1. Architectural Overview

The **Read Adapter** is responsible for all inbound file interactions against remote SFTP endpoints. It completely abstracts transport details, socket lifecycle, and directory traversals away from the orchestration and business layers.

```
┌────────────────────────────────────────────────────────┐
│                   Route Orchestrator                   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │   ReadAdapter   │ (Interface contract)
                   └────────┬────────┘
                            │
                            ▼
                 ┌────────────────────┐
                 │  SftpReadAdapter   │
                 └──────────┬─────────┘
                            │
      ┌─────────────────────┴─────────────────────┐
      ▼                                           ▼
┌──────────────────────────┐          ┌──────────────────────────┐
│ SftpSessionFactoryRegistry│         │    PathValidationUtil    │
│ (MINA SSHD + Pool Cache) │          │  (Path Traversal Guard)  │
└──────────────────────────┘          └──────────────────────────┘
```

---

## 2. Interface Contract (`ReadAdapter`)

| Method | Parameters | Returns | Description |
| :--- | :--- | :--- | :--- |
| `listFiles` | `(connectionId, remoteDirectory)` | `List<RemoteFileInfo>` | Lists all contents in the remote path, filtering out standard navigation entries (`.` and `..`). |
| `discoverFiles` | `(connectionId, remoteDirectory, filePattern)` | `List<RemoteFileInfo>` | Scans for ready-to-process files matching a glob pattern (e.g. `*.txt`). Automatically ignores directories and in-flight transfer files (`*.part`, `*.tmp`). |
| `downloadFile` | `(connectionId, remotePath, localPath, overwrite)` | `Path` | Streams remote file down to a temporary local staging file (`<file>.download.tmp`), then atomically renames to the target destination. Enforces overwrite protection. |
| `fileExists` | `(connectionId, remotePath)` | `boolean` | Verifies remote presence without reading content. |
| `getFileMetadata` | `(connectionId, remotePath)` | `Optional<RemoteFileInfo>` | Fetches attributes (size, modification timestamp, permissions). |
| `testConnection` | `(connectionId)` | `boolean` | Opens a live session, verifies socket state, and cleanly releases resources. |

---

## 3. Key Design & Reliability Features

### A. Atomic Download Staging
To guarantee that downstream validation and processing engines never ingest partially downloaded files, the `downloadFile` method implements a two-stage acquisition process:
1. Target file: `C:\workspace\downloads\positions.txt`
2. Staging file: `C:\workspace\downloads\positions.txt.download.tmp`
3. Download executes into the staging file.
4. On complete stream closure, `Files.move(..., StandardCopyOption.ATOMIC_MOVE)` atomically places the completed file at the final path.

### B. Path Traversal Protection
All remote paths are processed through `PathValidationUtil.validateAndNormalizeRemotePath(...)`.
- Forward and backward slashes are normalized.
- Any attempt to navigate outside the intended boundary using `..`, `/../`, or leading `../` triggers an immediate `ValidationException("PATH_TRAVERSAL_DETECTED")`.

### C. Session Caching & Lifecycle Management
- Implemented via `SftpSessionFactoryRegistry` using Spring Integration's `CachingSessionFactory`.
- Maintains a controlled pool of active SSH sessions per `connectionId` (pool size: 5, configurable wait timeout).
- All operations employ Java `try-with-resources` (`try (Session<SftpClient.DirEntry> session = openSession(connectionId))`), ensuring sessions are returned to the pool immediately upon completion, even in error scenarios.

### D. Modern Apache MINA SSHD Stack
- No legacy JSch is utilized.
- Built strictly on Spring Integration SFTP v7.2.0 + Apache MINA SSHD v2.20.1.
