# MFT Enterprise Integration Engine

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)
[![Spring Boot 4.2.0](https://img.shields.io/badge/Spring%20Boot-4.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-105%20Passing-success.svg)]()

An enterprise-grade, configuration-driven **Managed File Transfer (MFT)** engine built with **Java 21** and **Spring Boot 4.2.0**, featuring dual SFTP connectivity, automated SHA-256 integrity checks, persistent idempotency with a zero duplicate re-transfer guarantee, and a real-time reactive Web Control Dashboard.

---

## ⚡ Instant Download (No Git / Maven Required)

For end-users who do not want to clone the source code:

1. Download the pre-built standalone package from GitHub Releases:
   👉 **[Download mft-enterprise-engine-windows-x64.zip](https://github.com/CodesBySammy/mft_integration_engine/releases/latest)**
2. Unzip the package to any folder.
3. Double-click **`setup.bat`** (prompts for your custom folder locations or defaults, bootstraps SFTP servers, creates users, and launches the dashboards).

---

## Key Features

- **1-Click Automated Setup:** Run `setup.bat` to automatically provision everything: downloads SFTPGo portable binary, sets up Source and Destination SFTP servers, creates user credentials, compiles the engine, and opens all dashboards in your browser.
- **Zero Duplicate Re-Transfer Guarantee:** Files transferred to destination are cryptographically tracked using SHA-256 idempotency hashing persisted on disk. Once transferred, a file is **never repeatedly re-sent**, preventing destination folder spam.
- **Dual SFTP Architecture:**
  - **Source Server A:** Port `2022` (SFTP) & Port `9080` (WebAdmin)
  - **Destination Server B:** Port `2222` (SFTP) & Port `9081` (WebAdmin)
- **Controlled File Lifecycle:** Atomic downloads (`.tmp` -> rename), in-memory/disk transformation, and two-phase publications (`.part` -> final commit).
- **Processing Modes:**
  - `RAW_COPY`: Pass-through transfer with end-to-end SHA-256 verification.
  - `TRANSFORM`: Parses fixed-width financial records, converts to delimited records, calculates batch aggregates, and publishes companion `.sha256` + JSON metadata manifest.
- **Enterprise Resilience:** Configurable retry policies with exponential backoff and randomized jitter for network blips.
- **Audit & Observability:** Structured JSON audit logging, transfer lifecycle repository with disk persistence, and unified REST APIs.
- **Web Control Dashboard:** Live, reactive monitoring panel at `http://localhost:8080`.

---

## 🏢 Enterprise MFT Integration (IBM Sterling, Axway, MOVEit)

The engine was architected as a **12-Factor, Configuration-Driven Enterprise MFT-to-MFT Middleware Layer**.
While SFTPGo was included for local developer testing, shifting to commercial enterprise MFT systems like **IBM Sterling B2B Integrator / File Gateway** requires **ZERO Java code changes**. All endpoints, authentication (SSH Keys / Passwords), mailbox paths, and ciphers are controlled via `application-prod.yml` and environment variables.

👉 **[Read the Full Enterprise IBM Sterling MFT Integration & Migration Guide](docs/ENTERPRISE_IBM_MFT_INTEGRATION_GUIDE.md)**

---

## Architecture Diagram

```
+-----------------------------------------------------------------------------------+
|                           MFT INTEGRATION ENGINE                                  |
|                                                                                   |
|  +--------------------+     +---------------------+     +----------------------+  |
|  |    ReadAdapter     | --> | Processing Pipeline | --> |     WriteAdapter     |  |
|  | (MINA SSHD Session)|     | (RAW_COPY/TRANSFORM)|     |  (2-Phase .part Pub) |  |
|  +--------------------+     +---------------------+     +----------------------+  |
|            ^                           |                           |              |
|            |                           v                           v              |
|   [Download .tmp]             [SHA-256 Checksum]            [Atomic Rename]       |
+------------|---------------------------|---------------------------|--------------+
             |                           |                           |
             v                           v                           v
   +-------------------+       +--------------------+      +-------------------+
   |  Server A (Source)|       | Persistent Storage |      | Server B (Dest)   |
   |   Port 2022       |       |  - idempotency.json|      |   Port 2222       |
   |   /upload         |       |  - transfers.json  |      |   /incoming       |
   +-------------------+       +--------------------+      +-------------------+
```

> 📖 **Deep Dive**: See the comprehensive [Architecture & End-to-End File Flow Specification](docs/ARCHITECTURE_AND_FILE_FLOW.md) for full Mermaid sequence diagrams, state transition models, and security boundary guarantees.

---

## Quickstart (3 Easy Methods)

### Prerequisites
- **Windows OS**
- **Java 21 (JDK 21)** installed and available in PATH ([Download from Eclipse Adoptium](https://adoptium.net))

---

### Option 1: 1-Line Remote Installer (No Git Required)
Open PowerShell and run:
```powershell
irm https://raw.githubusercontent.com/CodesBySammy/mft_integration_engine/main/scripts/quick-install.ps1 | iex
```
*This downloads the engine, provisions the SFTP servers, configures users, and opens all dashboards automatically.*

---

### Option 2: Download Standalone ZIP (No Git Required)
1. Download the pre-built package:
   **[Download Latest Windows Standalone ZIP](https://github.com/CodesBySammy/mft_integration_engine/releases)**
2. Extract the ZIP file anywhere on your computer.
3. Double-click **`setup.bat`** (or `start-all.bat`).
4. That's it! Everything launches and opens in your browser immediately.

---

### Option 3: Git Clone (For Developers)
```powershell
git clone https://github.com/CodesBySammy/mft_integration_engine.git
cd mft_integration_engine
.\setup.bat
```

**What `setup.bat` automatically does:**
1. Checks for Java 21 runtime.
2. Automatically downloads portable SFTPGo for Windows if not present.
3. Provisions Server A (Port 2022) and Server B (Port 2222).
4. Sets up Source (`mft_source/upload`) and Destination (`mft_destination/incoming`) directories.
5. Pre-seeds SFTP users (`mft_source` and `mft_destination`).
6. Starts all 3 processes and automatically opens all dashboards in your browser!

---

## Endpoints & Dashboards

| Service | URL / Port | Description | Credentials |
| :--- | :--- | :--- | :--- |
| **MFT Web Dashboard** | `http://localhost:8080` | Real-time transfer ledger, manual route execution, status | None required |
| **Source Server A Admin** | `http://localhost:9080/web/admin` | SFTPGo Server A Web Management | `admin` (set on first login) |
| **Dest Server B Admin** | `http://localhost:9081/web/admin` | SFTPGo Server B Web Management | `admin` (set on first login) |
| **Source SFTP Server** | `sftp://localhost:2022` | SFTP Source endpoint (`/upload`) | `mft_source` / `source_pass` |
| **Dest SFTP Server** | `sftp://localhost:2222` | SFTP Destination endpoint (`/incoming`) | `mft_destination` / `dest_pass` |

---

## How to Test Transfer

1. Drop any file (e.g., `sample.txt`) into:
   ```text
   sftpgo\instance-a\data\mft_source\upload
   ```
2. Within 5 seconds, the engine detects the file, computes its SHA-256 hash, and delivers it to:
   ```text
   sftpgo\instance-b\data\mft_destination\incoming
   ```
3. Refresh or view `http://localhost:8080` to see the transfer record with a green **`COMPLETED`** badge, byte count, SHA-256 hash, and execution timestamp.
4. **Idempotency Guarantee:** The file will remain in `upload` without being transferred again and again.

---

## Daily Launchers

- **Start All Services:** Double-click `start-all.bat`
- **Stop All Services:** Double-click `stop-all.bat`

---

## REST API Reference

The engine exposes uniform JSON APIs:

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/health` | Comprehensive health check (SFTP connectivity, disk, routes) |
| `GET` | `/api/connections` | List configured SFTP connection profiles (passwords masked) |
| `GET` | `/api/routes` | List configured transfer routes |
| `POST` | `/api/routes/{routeId}/execute` | Manually trigger route transfer run |
| `GET` | `/api/transfers` | Query transfer history ledger |
| `GET` | `/api/transfers/{runId}` | Get details of a single transfer run |
| `GET` | `/api/transfers/{runId}/audit` | Get audit events for a specific transfer run |
| `GET` | `/api/audit` | Query complete audit events |
| `POST` | `/api/idempotency/clear` | Clear idempotency cache (allows reprocessing) |

---

## Detailed Documentation

Comprehensive documentation is available in [`mft-integration-engine/docs/`](mft-integration-engine/docs/):
- [Architecture & Design](mft-integration-engine/docs/ARCHITECTURE.md)
- [SFTP Connections & Connection Pooling](mft-integration-engine/docs/SFTP_CONNECTIONS.md)
- [Read Adapter & Polling Discovery](mft-integration-engine/docs/READ_ADAPTER.md)
- [Write Adapter & 2-Phase Publication](mft-integration-engine/docs/WRITE_ADAPTER.md)
- [Checksum Verification & Integrity](mft-integration-engine/docs/CHECKSUM_AND_INTEGRITY.md)
- [Retry Manager & Idempotency Engine](mft-integration-engine/docs/RETRY_AND_IDEMPOTENCY.md)
- [REST API Specifications](mft-integration-engine/docs/API_DOCUMENTATION.md)
- [Security & Credential Management](mft-integration-engine/docs/SECURITY.md)
- [Testing & Test Harness](mft-integration-engine/docs/TESTING.md)
- [Troubleshooting Guide](mft-integration-engine/docs/TROUBLESHOOTING.md)

---

## License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.
