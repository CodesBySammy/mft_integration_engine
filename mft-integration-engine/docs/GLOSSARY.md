# MFT Integration Glossary

Common terminology used throughout the Managed File Transfer (MFT) Integration Engine:

| Term | Definition |
| :--- | :--- |
| **MFT** | Managed File Transfer — Secure, reliable, audited data movement between enterprise systems. |
| **SFTP** | SSH File Transfer Protocol — Secure file transfer subsystem running over SSH-2. |
| **Apache MINA SSHD** | Pure Java SSH client/server library providing non-deprecated, high-performance SFTP capabilities. |
| **Read Adapter** | Interface and implementation responsible for remote file discovery, listing, existence checks, and atomic downloading. |
| **Write Adapter** | Interface and implementation responsible for remote directory creation, staging, and two-phase atomic publishing. |
| **Two-Phase Publication** | Staging technique where a file is uploaded to `<filename>.part` before being atomically renamed to its final target name, preventing consumers from reading partial files. |
| **Idempotency** | Property ensuring that duplicate processing of identical data produces the same result without duplicate downstream publications. |
| **Idempotency Key** | Cryptographically derived SHA-256 composite digest of route ID, source connection, file path, size, and source file checksum. |
| **Fail-Fast** | Design principle where deterministic errors (checksum mismatch, malformed layout) immediately abort without entering retry queues. |
| **Quarantine** | Isolated workspace directory where corrupted or rejected files are moved alongside structured diagnostic incident reports. |
| **Companion File** | Sidecar verification file (e.g., `.sha256`) containing the cryptographic digest of an associated payload file. |
| **Metadata Manifest** | Sidecar JSON file containing audit metadata (interface ID, record count, execution run ID, checksum) describing the accompanying data payload. |
| **Exponential Backoff** | Retry algorithm that progressively doubles the wait time between subsequent failed attempts, augmented with randomized jitter to prevent thundering herd spikes. |
| **TransferRun** | Persistent domain record tracking the end-to-end lifecycle and status transitions of an individual file transfer. |
| **AuditEvent** | Immutable structured event logging operational state transitions, timestamps, byte counts, and diagnostics. |
