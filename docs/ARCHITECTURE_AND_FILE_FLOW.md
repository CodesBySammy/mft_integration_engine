# MFT Enterprise Integration Engine: Architecture & End-to-End File Flow

---

## 1. Executive System Architecture

The **Managed File Transfer (MFT) Integration Engine** is an enterprise-grade file integration platform built in **Java 21** and **Spring Boot 4.2.0**. It orchestrates reliable, idempotent, and auditable file movements between heterogeneous endpoints (demonstrated with dual SFTP servers).

```mermaid
flowchart TB
    subgraph SourceZone ["Source Domain (Server A: Port 2022)"]
        SrcDir[("Remote Directory: /upload\n(Physical: mft_source/upload)")]
    end

    subgraph EngineCore ["MFT Integration Engine Core (Spring Boot on Port 8080)"]
        subgraph Ingress ["Ingress & Discovery"]
            Sched["RouteScheduler\n(Fixed Delay: 5000ms)"]
            REST["REST API Trigger\n(/api/routes/{id}/execute)"]
            ReadAdp["SftpReadAdapter\n(Apache MINA SSHD Pool)"]
        end

        subgraph SecurityIntegrity ["Integrity & Idempotency Layer"]
            LocalWS["LocalWorkspaceService\n(Boundary Enforcement)"]
            Sha256["ChecksumService\n(SHA-256 Engine)"]
            IdemReg["IdempotencyService\n(Persistent Registry)"]
            Retry["RetryManager\n(Backoff + Jitter)"]
        end

        subgraph Processors ["Processing Pipelines"]
            RawCopy["RawCopyProcessor\n(Pass-through + Hash)"]
            Transform["TransformationProcessor\n(Fixed-Width -> Delimited)"]
        end

        subgraph Egress ["Egress & Publication"]
            WriteAdp["SftpWriteAdapter\n(2-Phase Atomic Commit)"]
        end

        subgraph Ledger ["Observability & State"]
            StatusRepo["TransferStatusRepository\n(transfers_history.json)"]
            AuditRepo["AuditRepository\n(audit_history.json)"]
            WebDash["Web Control Dashboard\n(http://localhost:8080)"]
        end
    end

    subgraph DestZone ["Destination Domain (Server B: Port 2222)"]
        DstDir[("Remote Directory: /incoming\n(Physical: mft_destination/incoming)")]
    end

    %% Flow Connections
    Sched --> ReadAdp
    REST --> ReadAdp
    SrcDir -.->|"1. SFTP List / Glob Pattern"| ReadAdp
    ReadAdp -->|"2. Atomic SFTP Fetch (.tmp)"| LocalWS
    LocalWS -->|"3. Compute Hash"| Sha256
    Sha256 -->|"4. Check Hash & Size"| IdemReg
    IdemReg -->|"Duplicate Detected"| StatusRepo
    IdemReg -->|"5. New or Changed File"| Processors

    Processors -->|"RAW_COPY"| RawCopy
    Processors -->|"TRANSFORM"| Transform

    RawCopy -->|"6. Prepared Artifact"| WriteAdp
    Transform -->|"6. Data + .sha256 + .manifest"| WriteAdp

    WriteAdp -->|"7. Phase 1: Upload (.part)"| DstDir
    WriteAdp -->|"8. Phase 2: Atomic Rename"| DstDir

    WriteAdp -->|"9. Commit Idempotency"| IdemReg
    WriteAdp -->|"10. Update Status COMPLETED"| StatusRepo
    WriteAdp -->|"11. Record Structured Audit"| AuditRepo
    StatusRepo -.->|"Live Polling (2s)"| WebDash
    AuditRepo -.->|"Live Polling (2s)"| WebDash
```

---

## 2. End-to-End File Lifecycle Sequence

The sequence below traces a single file transfer run from discovery through publication:

```mermaid
sequenceDiagram
    autonumber
    actor Scheduler as Background Poller / API
    participant ReadAdapter as SftpReadAdapter
    participant ServerA as Source SFTP (Port 2022)
    participant Workspace as LocalWorkspaceService
    participant Checksum as ChecksumService
    participant Idempotency as IdempotencyService
    participant Processor as Processing Pipeline
    participant WriteAdapter as SftpWriteAdapter
    participant ServerB as Dest SFTP (Port 2222)
    participant StatusAudit as Status & Audit Repositories

    Scheduler->>ReadAdapter: executeRoute(routeId)
    ReadAdapter->>ServerA: discoverFiles(dir: /upload, pattern: *.txt)
    ServerA-->>ReadAdapter: List<RemoteFileInfo> [test.txt, 17 bytes]

    critical Ingress Phase
        ReadAdapter->>Workspace: resolvePath(DOWNLOADS, "test.txt.tmp")
        ReadAdapter->>ServerA: readRemoteStream(remotePath)
        ServerA-->>ReadAdapter: Raw Byte Stream
        ReadAdapter->>Workspace: Write bytes to test.txt.tmp
        ReadAdapter->>Workspace: Atomic Rename (test.txt.tmp -> test.txt)
        StatusAudit->>StatusAudit: Status: DOWNLOADED
    end

    critical Cryptographic Deduplication Phase
        Checksum->>Workspace: calculateChecksum(test.txt, SHA-256)
        Checksum-->>Idempotency: SHA-256: f2ca1bb6...
        Idempotency->>Idempotency: Check Key: routeId + srcConn + path + size + hash
        alt Already Transferred & allowReprocessing == false
            Idempotency-->>Scheduler: Duplicate detected -> SKIP
            Note over Scheduler,ServerB: File remains in source, no data sent to destination
        else New or Modified File
            Idempotency->>Idempotency: Register Status: IN_PROGRESS
            StatusAudit->>StatusAudit: Status: PROCESSING
        end
    end

    critical Transformation / Copy Phase
        alt ProcessingMode == RAW_COPY
            Processor->>Workspace: RawCopyProcessor.process(test.txt)
            Processor-->>WriteAdapter: Verified Path + Source Checksum
        else ProcessingMode == TRANSFORM
            Processor->>Workspace: TransformationProcessor.transform(trades.dat)
            Note over Processor: Fixed-width parsing -> Pipe-delimited CSV<br/>Calculate total count & amount<br/>Generate companion .sha256 and .manifest.json
            Processor-->>WriteAdapter: Output file + Companion + Manifest
        end
        StatusAudit->>StatusAudit: Status: PROCESSED
    end

    critical Two-Phase Publication Phase
        WriteAdapter->>ServerB: uploadStream(file, "/incoming/test.txt.part")
        ServerB-->>WriteAdapter: Upload complete
        WriteAdapter->>ServerB: sftpRename("/incoming/test.txt.part", "/incoming/test.txt")
        ServerB-->>WriteAdapter: Rename confirmed
        WriteAdapter->>ServerB: verifyRemoteFileExistsAndSize("/incoming/test.txt")
        ServerB-->>WriteAdapter: Verification OK
        StatusAudit->>StatusAudit: Status: PUBLISHING -> COMPLETED
    end

    critical Finalization Phase
        WriteAdapter->>Idempotency: markCompleted(key, destinationPath)
        Note over Idempotency: Flushed to idempotency_records.json
        WriteAdapter->>StatusAudit: registerRun(TransferRun: COMPLETED)
        Note over StatusAudit: Flushed to transfers_history.json & audit_history.json
        StatusAudit-->>Scheduler: Execution Summary: Transferred=1, Skipped=0
    end
```

---

## 3. Core Architectural Subsystems

### 3.1. Dual SFTP Adapter Architecture
* **MINA SSHD Session Pooling:** Connections are managed by `CachingSessionFactory` backed by Apache MINA SSHD. Connections remain open and cached to prevent expensive SSH key-exchange overhead on periodic 5-second polling intervals.
* **Separation of Concerns:**
  * **Source Server A (Ingress):** Port `2022`, pre-seeded with user `mft_source`. Granted download and delete permissions only.
  * **Destination Server B (Egress):** Port `2222`, pre-seeded with user `mft_destination`. Granted full publication permissions (`upload`, `overwrite`, `rename`, `create_dirs`).

### 3.2. Controlled Local Workspace Confinement
To protect against path-traversal vulnerabilities (`..` directory climbing) and guarantee multi-tenant filesystem safety, all file operations occur inside a strictly bounded local workspace:

```
workspace/
├── downloads/      # Incoming raw transfers (atomic .tmp downloads)
├── staging/        # Intermediate files being transformed or enriched
├── output/         # Transformed files, companion .sha256, and .manifest.json
├── quarantine/     # Checksum mismatches or corrupted files isolated here
└── status/         # Persistent JSON state ledgers (survives restarts)
    ├── transfers_history.json
    ├── idempotency_records.json
    └── audit_history.json
```
* **Boundary Validation:** Every file path is validated via `PathValidationUtil.validateLocalWorkspacePath(target, root)`. If an incoming file name contains directory traversal sequences (`../../etc/passwd`), the engine immediately throws a `SecurityException` and quarantines the transaction.

### 3.3. Zero Duplicate Re-Transfer Guarantee (Cryptographic Idempotency)
Unlike naive file transfer tools that compare only file names or modification dates, this engine computes a deterministic SHA-256 idempotency key:

$$\text{Key} = \text{SHA-256}(\text{routeId} : \text{sourceConnectionId} : \text{sourceFilePath} : \text{fileSize} : \text{sourceSha256})$$

* **Disk Persistence:** Records are serialized to disk (`idempotency_records.json`).
* **Engine Boot Rehydration:** When the Spring Boot application starts or restarts, all previously completed hashes are loaded into memory.
* **Guarantee:** If a file sits permanently in `/upload`, the engine identifies it within 5 milliseconds, logs a debug skip, and **never uploads it to destination again**. If the user updates the file content, the SHA-256 hash changes, the key changes, and the new version is immediately transferred.

### 3.4. Two-Phase Atomic Publication Protocol
To prevent downstream systems from consuming half-written or locked files during large network transfers:
1. **Phase 1 (Upload):** The file is streamed to the destination under a temporary suffix:
   `/incoming/payload.dat.part`
2. **Flush & Sync:** The engine flushes all SSH stream buffers and verifies byte count against local payload size.
3. **Phase 2 (Atomic Commit):** The remote SFTP server issues an atomic rename:
   `/incoming/payload.dat.part` $\longrightarrow$ `/incoming/payload.dat`
4. **Post-Commit Verification:** A remote SFTP `stat` query confirms the final file exists and matches the byte length before recording the transfer as `COMPLETED`.

### 3.5. Dual Processing Modes

#### A. `RAW_COPY`
* Direct binary pass-through.
* End-to-end SHA-256 validation (Source hash == Destination hash).
* Zero transformation overhead; ideal for arbitrary document transfers.

#### B. `TRANSFORM`
* **Source:** Fixed-width financial transaction records (`trades.dat`).
* **Processing:**
  1. Parses fixed-width columnar records (Trade ID, Account, Security, Quantity, Price, Currency).
  2. Converts to standard pipe-delimited format (`|`).
  3. Computes batch summary aggregates (Total records, Total settlement amount).
* **Publication Trinity:**
  1. Primary data file: `SL_RECON_YYYYMMDD_<runId>.txt`
  2. Companion checksum: `SL_RECON_YYYYMMDD_<runId>.txt.sha256`
  3. Audit metadata manifest: `SL_RECON_YYYYMMDD_<runId>.txt.manifest.json`

---

## 4. State Transition Diagram

Every file lifecycle is mapped through explicit, immutable status transitions:

```mermaid
stateDiagram-v2
    [*] --> DISCOVERED : ReadAdapter discovers file matching pattern
    DISCOVERED --> DOWNLOADING : Atomic SFTP stream initiated
    DOWNLOADING --> DOWNLOADED : Local .tmp renamed to target
    DOWNLOADING --> FAILED : Network timeout / SFTP read drop (Triggers Retry)

    DOWNLOADED --> SKIPPED_DUPLICATE : SHA-256 Hash matches existing completed record
    SKIPPED_DUPLICATE --> [*] : Poller skips silently, retains existing history

    DOWNLOADED --> PROCESSING : Valid new or modified file
    PROCESSING --> PROCESSED : RAW_COPY verified / TRANSFORM completed
    PROCESSING --> QUARANTINED : Layout error / Corrupt structure

    PROCESSED --> PUBLISHING : Phase 1: Uploading .part file
    PUBLISHING --> COMPLETED : Phase 2: Atomic rename + Verification passed
    PUBLISHING --> FAILED : Destination write drop (Triggers Retry)

    QUARANTINED --> [*]
    FAILED --> [*]
    COMPLETED --> [*]
```

---

## 5. Directory & File Mapping Table

| Logical Domain | SFTP Server Path | Local / Physical Host Path | Managed User / Permissions |
| :--- | :--- | :--- | :--- |
| **Source Ingress** | `/upload` | `.../sftpgo/instance-a/data/mft_source/upload` | `mft_source` (`list`, `download`, `delete`) |
| **Destination Egress** | `/incoming` | `.../sftpgo/instance-b/data/mft_destination/incoming` | `mft_destination` (`list`, `download`, `upload`, `overwrite`, `rename`) |
| **Local Staging** | *N/A (Local Engine)* | `.../workspace/downloads/` & `staging/` | Engine Process (Confined boundary) |
| **Quarantine Isolation** | *N/A (Local Engine)* | `.../workspace/quarantine/<runId>/` | Engine Process (Failure reason metadata) |
| **State Registry** | *N/A (Local Engine)* | `.../workspace/status/transfers_history.json` | Persistent ledger for Web Dashboard |
| **Idempotency Cache** | *N/A (Local Engine)* | `.../workspace/status/idempotency_records.json` | SHA-256 deduplication state |

---

## 6. Real-Time Observability & Monitoring

The engine exposes three observability tiers:
1. **Interactive Web Dashboard (`http://localhost:8080`):**
   * Auto-refreshes every 2 seconds via reactive polling.
   * Displays deduplicated transfer history table showing each file's route, status (`COMPLETED`, `FAILED`), byte size, SHA-256 hash, and transfer timestamp.
   * 1-click manual trigger buttons for on-demand execution.
2. **REST APIs:**
   * `/api/health`: Health status of connections, routes, and disk.
   * `/api/transfers`: Full lifecycle history queryable by `routeId` or `runId`.
   * `/api/audit`: Immutable compliance audit trail.
3. **SFTP Administration Dashboards:**
   * Server A Management: `http://localhost:9080/web/admin`
   * Server B Management: `http://localhost:9081/web/admin`
