# Retry Policy and Idempotency Architecture

This document describes the design, implementation, and operational behavior of the Retry Mechanism and Idempotency Subsystem in the MFT Integration Engine.

---

## 1. Idempotency Subsystem

### 1.1 Purpose
In enterprise Managed File Transfer (MFT), duplicate deliveries of identical files (e.g. resends, partner retry loops, or network replays) can cause corrupted downstream databases or duplicate accounting entries. The Idempotency Subsystem guarantees that each unique source file is processed and published to the destination only once, unless explicitly configured with `allowReprocessing = true`.

### 1.2 Idempotency Key Composition
The `IdempotencyKey` is a deterministic composite key built from the physical attributes of the discovered source file and route configuration:

$$\text{Key} = \text{routeId} : \text{sourceConnectionId} : \text{sourceFilePath} : \text{fileSize} : \text{sourceSha256}$$

The key produces a 64-character hexadecimal SHA-256 digest:
```
d7a41cc49db01db0a08156dded265c629145bddd0178368d63f68a83d46a8fbd
```

### 1.3 Transfer State Machine
Each transfer records its state in `IdempotencyRecord`:
- **`IN_PROGRESS`**: Registered right after download and source checksum calculation, prior to transformation/publishing.
- **`COMPLETED`**: Transitioned immediately after successful atomic publication on the destination SFTP server.
- **`FAILED`**: Transitioned if transformation, integrity verification, or destination publication fails.

### 1.4 Reprocessing Behavior
In `RouteDefinition`:
```yaml
mft:
  routes:
    R_RAW_COPY:
      allow-reprocessing: false # (default)
```
- When `allowReprocessing: false`: If a file matching the identical SHA-256 hash has already reached `COMPLETED` status, `RouteOrchestrator` skips the file, marks `TransferStatus.SKIPPED_DUPLICATE`, logs an audit event (`DUPLICATE_FILE_SKIPPED`), and continues without re-uploading.
- When `allowReprocessing: true`: The engine logs a warning audit event and permits re-processing and re-publishing of the file.

---

## 2. Retry Policy Subsystem

### 2.1 Purpose
Network interruptions, transient socket resets, and temporary SFTP connection drops must be retried transparently. However, logical errors (corrupted records, invalid schemas, checksum mismatches) are deterministic and must **fail fast** without wasting compute or flooding retry queues.

### 2.2 Retry Discrimination (`isRetryable`)
| Exception Type | Retryable? | Reason |
| :--- | :---: | :--- |
| `SftpConnectionException` | **Yes** | Transient SSH connection or network session loss. |
| `IOException` / `SocketException` | **Yes** | Transient pipe or network socket interruption. |
| `IntegrityException` | **No** | Checksum verification mismatch indicates corrupted or truncated file; must be quarantined immediately. |
| `ValidationException` | **No** | Domain constraint violation (e.g., negative amount, bad date); retrying will never fix bad data. |
| `FileProcessingException` | **No** | Malformed record layout (record length mismatch). |

### 2.3 Exponential Backoff with Jitter
The `RetryPolicy` calculates backoff delay dynamically:

$$\text{delay} = \min\left(\text{initialDelay} \times \text{multiplier}^{\text{attempt}-1}, \text{maxDelay}\right) \pm \text{jitter}$$

Configuration in `RouteDefinition`:
- `retryLimit`: Default `3` attempts.
- `retryInitialDelayMs`: Default `500` ms.
- `retryMultiplier`: Default `2.0`.
- `maxDelayMs`: Default `10,000` ms (10 seconds).
- `jitter`: Default `0.1` (10% randomized spread to avoid thundering herd).

---

## 3. Repositories

- `IdempotencyRepository`: Provides CRUD abstractions for idempotency records. Implemented in-memory via `InMemoryIdempotencyRepository` (backed by `ConcurrentHashMap`), designed for seamless migration to PostgreSQL / JDBC.
- `TransferStatusRepository`: Stores active and completed `TransferRun` lifecycle states.
- `AuditRepository`: Stores structured, immutable `AuditEvent` records.
