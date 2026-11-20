# Write Adapter Architecture & Publication Guide

## 1. Architectural Overview

The **Write Adapter** handles all outbound file delivery and remote publication. It implements safe atomic publication semantics to ensure remote consumers never consume half-written or in-flight files.

```
┌────────────────────────────────────────────────────────┐
│                   Route Orchestrator                   │
└───────────────────────────┬────────────────────────────┘
                            │ (connectionId, remoteDir, filename)
                            ▼
                   ┌──────────────────┐
                   │   WriteAdapter   │ (Interface contract)
                   └────────┬─────────┘
                            │
                            ▼
                 ┌────────────────────┐
                 │  SftpWriteAdapter  │
                 └──────────┬─────────┘
                            │
      ┌─────────────────────┼─────────────────────┐
      ▼                     ▼                     ▼
┌──────────────────┐ ┌───────────────┐ ┌──────────────────────┐
│ SftpSessionCache │ │PathValidation │ │ 2-Phase Atomic Staging│
│(MINA SSHD Pool:5)│ │(Anti-Traversal│ │(1: upload .part     │
│                  │ │ Guard)        │ │ 2: remote rename)    │
└──────────────────┘ └───────────────┘ └──────────────────────┘
```

---

## 2. Interface Contract (`WriteAdapter`)

| Method | Parameters | Returns | Description |
| :--- | :--- | :--- | :--- |
| `uploadFile` | `(connId, localPath, remotePath, overwrite)` | `TransferResult` | Streams local file directly to target remote path. Enforces overwrite protection. |
| `uploadFile` | `(connId, localPath, remoteDir, filename, overwrite)` | `TransferResult` | Convenience overload combining remote directory and filename safely. |
| `remoteFileExists` | `(connId, remotePath)` | `boolean` | Checks if a file exists on the destination server. |
| `createDirectory` | `(connId, remoteDir)` | `void` | Recursively creates missing remote directory paths. |
| `renameRemoteFile` | `(connId, sourcePath, destPath)` | `void` | Renames an existing remote file. |
| `publishFile` | `(connId, localPath, remoteDir, filename, overwrite)` | `TransferResult` | Executes 2-phase publication: uploads to `<filename>.part` first, then atomically renames to `<filename>`. |

---

## 3. Two-Phase Safe Publication Strategy

When publishing files across network boundaries, remote polling agents might discover files while they are still being written by the SFTP stream. To prevent partial ingestion:

1. **Phase 1 (Staging):**
   - File is uploaded with temporary staging extension:
     `SL_RECON_20261120_RUN001.txt.part`
   - File is completely closed and flushed on the SFTP server.
2. **Phase 2 (Publication Rename):**
   - The remote SFTP `rename` command is issued:
     `SL_RECON_20261120_RUN001.txt.part` -> `SL_RECON_20261120_RUN001.txt`
   - On standard filesystems, rename within the same directory/mount is atomic.
   - Downstream consumers waiting for `*.txt` only ever see the final, complete file.

---

## 4. Verification

Live verification was performed against Destination SFTPGo Server B (port 2222) via `SftpWriteLiveIntegrationTest`:
- Uploaded staging file: `/incoming/SL_RECON_20261120_RUN001.txt.part`
- Atomically renamed to: `/incoming/SL_RECON_20261120_RUN001.txt`
- Read back via `ReadAdapter` and verified byte-for-byte identical.
