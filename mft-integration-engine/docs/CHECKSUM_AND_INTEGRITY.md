# Checksum Verification & Local Workspace Architecture

## 1. Checksum & Integrity Service (`ChecksumService`)

File transfer integrity is guaranteed through cryptographic SHA-256 verification:

```
┌────────────────────────────────────────────────────────┐
│                   Route Orchestrator                   │
└───────────────────────────┬────────────────────────────┘
                            │
               ┌────────────┴────────────┐
               ▼                         ▼
      ┌─────────────────┐       ┌─────────────────┐
      │ ChecksumService │       │LocalWorkspaceSvc│
      └────────┬────────┘       └────────┬────────┘
               │                         │
     ┌─────────┴─────────┐               ├── downloads/
     ▼                   ▼               ├── processing/
┌──────────────┐   ┌─────────────┐       ├── output/
│ SHA-256 Hash │   │ .sha256     │       ├── quarantine/
│ (64-char hex)│   │ Companion   │       ├── archive/
└──────────────┘   └─────────────┘       └── temp/
```

### Supported Operations:
1. **Streaming Digest Calculation:** Computes SHA-256 across arbitrary file sizes using 64KB buffers without loading entire files into memory.
2. **Companion File Parsing:** Reads `.sha256` files in all standard formats:
   - GNU text: `e3b0c442...  positions.txt`
   - GNU binary: `e3b0c442... *positions.txt`
   - Bare hex digest: `e3b0c442...`
3. **Companion File Generation:** Emits standard `<hash>  <filename>` companion files for publication.
4. **Mismatch Handling:** If calculated hash does not match expected, throws `IntegrityException` and initiates quarantine.

---

## 2. Controlled Local Workspace (`LocalWorkspaceService`)

To prevent arbitrary filesystem manipulation, all file operations occur within a strictly contained workspace (default: `D:\mft\workspace`):

| Directory | Purpose |
| :--- | :--- |
| `downloads/` | Staging area where files from `ReadAdapter.downloadFile` are stored. |
| `processing/` | Workspace where records are parsed, validated, and transformed. |
| `output/` | Destination for generated output payloads, companion checksums, and manifests. |
| `quarantine/` | Isolated subdirectories (`quarantine/<runId>/`) where corrupted files and reason audit files are moved. |
| `archive/` | Historical storage for successfully processed inputs. |
| `temp/` | Ephemeral scratch directory automatically purged after route runs. |

### Path Confinement:
All paths resolved through `LocalWorkspaceService.resolvePath(dir, filename)` are validated via `PathValidationUtil.validateLocalWorkspacePath(...)`. Any attempt to escape via `../../` is rejected before any I/O occurs.
