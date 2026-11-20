# MFT Integration Engine Architecture

This document details the architectural layers, interaction patterns, design principles, and component responsibilities of the MFT Integration Engine.

---

## 1. Architectural Principles

1. **Separation of Concerns**:
   - The Orchestration Engine does not contain raw SFTP session logic.
   - The Transformation and Parsing engines do not connect to network or SFTP endpoints.
   - REST Controllers delegate all operations to orchestrators and registries; controllers never execute SFTP protocol commands directly.
   - Read Adapters and Write Adapters are decoupled and independently testable.
2. **Defensive Confinement**:
   - Remote paths are strictly checked against directory traversal (`..`) attempts.
   - Local operations are confined within the structured lifecycle directory `workspace/`.
3. **Fail-Fast Integrity Verification**:
   - Corrupt files or checksum mismatches fail immediately without entering retry loops. Corrupt artifacts are quarantined with incident logs.
4. **Deterministic Idempotency**:
   - Every file transfer is tracked by a cryptographically unique key derived from content, size, path, and route. Duplicate deliveries are detected and skipped.

---

## 2. Component Diagram

```
+-----------------------------------------------------------------------------------+
|                                  REST API LAYER                                   |
|   ConnectionController | FileController | RouteController | TransferController    |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                              ORCHESTRATION LAYER                                  |
|                               RouteOrchestrator                                   |
|          Coordinates Discovery -> Download -> Verification -> Process -> Publish  |
+-----------------------------------------------------------------------------------+
       |                                   |                                  |
       v                                   v                                  v
+---------------+                +-------------------+               +---------------+
| READ ADAPTER  |                | PROCESSING ENGINE |               | WRITE ADAPTER |
| SftpReadAdapter|               | RawCopyProcessor  |               |SftpWriteAdapter|
| CachingSession|                |TransformationProc |               | 2-Phase Staging|
| MINA SSHD     |                | Fixed/Delimited   |               | Atomic Rename |
+---------------+                +-------------------+               +---------------+
       |                                   |                                  |
       +-----------------------------------+----------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------+
|                        CROSS-CUTTING FOUNDATION SERVICES                          |
|  IdempotencyService  |  RetryManager  |  ChecksumService  |  LocalWorkspaceService|
|  AuditService        |  TransferStatusService  |  CredentialProvider              |
+-----------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------+
|                             PERSISTENCE ABSTRACTIONS                              |
|   IdempotencyRepository  |  TransferStatusRepository  |  AuditRepository          |
|   (In-Memory Thread-Safe Implementations -> Ready for JPA/PostgreSQL extension)   |
+-----------------------------------------------------------------------------------+
```

---

## 3. Data Flow Pathways

### Pathway A: `RAW_COPY`
1. **Discovery**: `ReadAdapter.discoverFiles` lists files on Source SFTP matching pattern.
2. **Staged Download**: `ReadAdapter.downloadFile` streams remote bytes into `.download.tmp`, then atomically renames to final download path.
3. **Idempotency Check**: Computes SHA-256 and size; verifies if already completed. If duplicate, skips transfer.
4. **Binary Verification**: `RawCopyProcessor` copies stream to `output/` and re-verifies matching SHA-256.
5. **Two-Phase Publication**: `WriteAdapter.publishFile` uploads to `destination/filename.part`, then atomically renames to `destination/filename`.
6. **Audit & Completion**: Marks `IdempotencyRecord` completed, updates `TransferRun` to `COMPLETED`, records structured audit events.

### Pathway B: `TRANSFORM`
1. Steps 1-3 identical to Pathway A.
2. **Parsing**: `FixedWidthLayoutParser` parses fixed-width byte boundaries into typed `PositionRecord`s.
3. **Validation**: Validates account numbers, ISO 4217 currencies, dates, amounts, and record totals.
4. **Transformation**: Generates pipe-delimited file with standard `HDR`/`REC`/`TRL` records.
5. **Companion & Manifest**: Computes SHA-256 of delimited output, emits companion `.sha256` file, and serializes JSON metadata manifest.
6. **Multi-File Publication**: Publishes data file (`.part` -> rename), companion file, and manifest file sequentially.
