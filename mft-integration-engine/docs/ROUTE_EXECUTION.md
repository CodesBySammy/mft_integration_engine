# Route Execution Architecture: Raw Copy (`R_RAW_COPY`)

## 1. Architectural Pipeline

The **Route Orchestrator** manages the complete end-to-end file transfer lifecycle across decoupled adapters:

```
Configuration Registry (mft.routes.R_RAW_COPY)
                     │
                     ▼
             RouteOrchestrator
                     │
                     ▼
      ReadAdapter.discoverFiles() ──► Source SFTP (Server A: port 2022)
                     │
                     ▼
      ReadAdapter.downloadFile()  ──► Local Workspace (/workspace/downloads)
                     │
                     ▼
             RawCopyProcessor     ──► Calculate SHA-256, preserve raw bytes
                     │
                     ▼
     WriteAdapter.publishFile()   ──► Phase 1: Upload /incoming/positions.txt.part
                                  ──► Phase 2: Atomic rename to /incoming/positions.txt
                     │
                     ▼
         TransferStatus & Audit   ──► Record completion, SHA-256 checksums, runId
```

---

## 2. Route Configuration: `R_RAW_COPY`

Configured in `application-local.yml`:
```yaml
mft:
  routes:
    R_RAW_COPY:
      route-id: R_RAW_COPY
      source-connection-id: C_FILE_IN
      destination-connection-id: C_FILE_OUT
      processing-mode: RAW_COPY
      source-directory: /upload
      source-pattern: "*.txt"
      destination-directory: /incoming
      checksum-algorithm: SHA_256
      retry-limit: 3
      enabled: true
```

---

## 3. Byte Preservation & Cryptographic Integrity

1. **No Charset Alteration:** Files are copied via binary streams (`Files.copy`), ensuring character encodings (ASCII, UTF-8, binary) are completely preserved.
2. **Double Checksum Verification:**
   - Source Checksum ($H_{src}$) computed immediately after download.
   - Output Checksum ($H_{out}$) computed after output staging.
   - If $H_{src} \neq H_{out}$, transfer is aborted with `IntegrityException` and destination publication never triggers.

---

## 4. End-to-End Live Verification

Automated test `RawCopyLiveRouteIntegrationTest` verified:
- Discovered `positions.txt` on Source SFTP Server A (port 2022).
- Downloaded and processed via `RawCopyProcessor`.
- Published to Destination SFTP Server B (port 2222) via two-phase rename.
- Verified byte-for-byte equality ($H_{src} = H_{out}$).
