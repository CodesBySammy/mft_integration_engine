# Enterprise IBM Sterling MFT-to-MFT Integration & Configuration Guide

This guide provides the complete, end-to-end production specification for deploying the **MFT Integration Engine** as an enterprise **MFT-to-MFT Platform Layer** between two commercial MFT systems, using **IBM Sterling B2B Integrator (B2Bi)** and **IBM Sterling File Gateway (SFG)** as the reference enterprise architecture.

---

## 1. Architectural Blueprint: The Zero-Code-Change Principle

### Do You Need to Change Any Java Code Files?
**No. Absolutely zero Java code changes are required.**

The MFT Integration Engine was designed from the ground up as a **12-Factor, Configuration-Driven Enterprise Platform**. 

- **SFTPGo** was simply a lightweight local mock adapter used during development and unit testing.
- The underlying engine utilizes **Apache MINA SSHD** through Spring Integration, which strictly adheres to official IETF SSHv2 standards (**RFC 4251, 4252, 4253, 4254**).
- **IBM Sterling B2B Integrator / File Gateway** exposes standard **SFTP Server Adapters** (SSH File Transfer Protocol listeners).
- Therefore, migrating from local testing to commercial IBM Sterling MFT instances requires **only configuration updates (`application-prod.yml`)**, credential provisioning, and IBM Sterling mailbox setup.

```
┌──────────────────────────────────────┐                       ┌──────────────────────────────────────┐
│       SOURCE ENTERPRISE MFT          │                       │     DESTINATION ENTERPRISE MFT       │
│    (IBM Sterling B2Bi / Gateway)     │                       │    (IBM Sterling B2Bi / Gateway)     │
│  Host: ibm-source.corp.internal      │                       │  Host: ibm-dest.corp.internal        │
│  Port: 10022                         │                       │  Port: 10022                         │
│  Mailbox: /Mailbox/PartnerA/upload   │                       │  Mailbox: /Mailbox/CoreBank/incoming │
└──────────────────┬───────────────────┘                       └──────────────────▲───────────────────┘
                   │                                                              │
                   │ Standard SSHv2/SFTP (Port 10022)                             │ Standard SSHv2/SFTP (Port 10022)
                   ▼                                                              │
══════════════════════════════════════════════════════════════════════════════════╪════════════════════════
                             MFT-TO-MFT INTEGRATION ENGINE                        │
══════════════════════════════════════════════════════════════════════════════════╪════════════════════════
  [1] Polling & Discovery ───> Discovers files on IBM Source Mailbox
  [2] Cryptographic Deduplication ───> SHA-256 composite check against idempotency ledger
  [3] Secure Staging & Ingestion ───> Isolated download with keep-alive heartbeats
  [4] Data & Schema Transformation ───> Fixed-width to Delimited CSV + Control Trailers
  [5] Manifest Generation ───> Emits signed companion .sha256 and .manifest.json
  [6] Atomic Two-Phase Commit ───> Streams to .part staging on IBM Dest Mailbox ──> Atomic Rename
  [7] Audit Trail Ledger ───> Logs runId, timestamps, byte counts, and latency for compliance
═══════════════════════════════════════════════════════════════════════════════════════════════════════════
```

---

## 2. Step-by-Step Configuration in IBM Sterling MFT

You must configure both the **Source IBM MFT** instance and the **Destination IBM MFT** instance. Below are the exact administrative steps inside the IBM Sterling B2B Integrator / File Gateway Administration UI.

---

### Step 2.1: Verify or Create the SFTP Server Adapter in IBM Sterling

1. Log into **IBM Sterling B2B Integrator** dashboard (`https://<ibm-mft-host>:port/dashboard`).
2. Navigate to: **Deployment** $\rightarrow$ **Services** $\rightarrow$ **Configuration**.
3. Search for: `SFTP Server Adapter`.
4. Ensure an adapter is active, or create a new one:
   - **Name:** `Corp_SFTP_Server_Adapter`
   - **Port:** `10022` (or standard `22`)
   - **Host Identity Key:** Select your enterprise private host key (RSA 2048/4096 or ECDSA).
   - **Allowed Ciphers:** `aes256-gcm@openssh.com`, `aes256-ctr`, `chacha20-poly1305@openssh.com`
   - **Allowed MACs:** `hmac-sha2-512`, `hmac-sha2-256`
   - **User Authentication:** Enable `Password` and/or `Public Key`.

---

### Step 2.2: Create the Mailbox Hierarchy

In IBM Sterling, folders are organized as **Mailboxes**.

1. Navigate to: **Deployment** $\rightarrow$ **Mailboxes** $\rightarrow$ **Configuration**.
2. Create the **Source Mailbox** on the Source IBM MFT:
   - Path: `/Mailbox/PartnerA/upload`
   - Description: *Inbound dropzone for Partner A trade files*
3. Create the **Destination Mailbox** on the Destination IBM MFT:
   - Path: `/Mailbox/CoreBanking/incoming`
   - Description: *Ingestion target for processed financial positions*

---

### Step 2.3: Create Service Accounts (Trading Partners)

Create dedicated, least-privilege service accounts for our MFT Engine.

#### On Source IBM MFT:
1. Navigate to: **Accounts** $\rightarrow$ **User Accounts**.
2. Click **Add New User**:
   - **User ID:** `mft_bridge_in`
   - **Authentication:** `Local` or `LDAP/Active Directory`
   - **Base Mailbox:** `/Mailbox/PartnerA/upload`
3. Assign Mailbox Permissions:
   - `Extract` (Download files)
   - `View` (List files)
   - `Delete` (Delete after processing, if archiving/cleanup is enabled)

#### On Destination IBM MFT:
1. Navigate to: **Accounts** $\rightarrow$ **User Accounts**.
2. Click **Add New User**:
   - **User ID:** `mft_bridge_out`
   - **Authentication:** `Local` or `LDAP/Active Directory`
   - **Base Mailbox:** `/Mailbox/CoreBanking/incoming`
3. Assign Mailbox Permissions:
   - `Append` (Upload files)
   - `View` (Verify directory contents)
   - `Delete` (Allow overwrite/atomic rename of `.part` staging files)

---

### Step 2.4: Set Up Enterprise SSH Key Authentication (Recommended)

Production enterprise environments require SSH key-pair authentication instead of plain passwords.

1. **Generate an RSA 4096 or Ed25519 keypair** for the MFT Engine:
   ```bash
   ssh-keygen -t rsa -b 4096 -m PEM -f config/keys/ibm_mft_engine_rsa -C "mft-engine@company.com"
   ```
   This generates:
   - Private Key: `config/keys/ibm_mft_engine_rsa` (kept secure on the MFT Engine server).
   - Public Key: `config/keys/ibm_mft_engine_rsa.pub` (imported into IBM Sterling).

2. **Import the Public Key into IBM Sterling:**
   - Log into IBM Sterling B2Bi.
   - Navigate to: **Trading Partner** $\rightarrow$ **SSH** $\rightarrow$ **Authorized User Keys**.
   - Click **Check In New Key**.
   - Select `ibm_mft_engine_rsa.pub`.
   - Name the key: `MFT_ENGINE_AUTH_KEY`.
   - In **User Accounts** for `mft_bridge_in` and `mft_bridge_out`, link this key under **SSH Authorized User Keys**.

3. **Obtain the IBM Sterling Host Key (for `known_hosts`):**
   - Query the host key fingerprint of your IBM Sterling server:
     ```bash
     ssh-keyscan -p 10022 ibm-source.corp.internal > config/keys/known_hosts
     ssh-keyscan -p 10022 ibm-dest.corp.internal >> config/keys/known_hosts
     ```

---

## 3. Configuring the MFT Engine for IBM Sterling

All settings are configured inside `src/main/resources/application-prod.yml` (or via external environment variables on the production server).

### Production Configuration File: `application-prod.yml`

```yaml
# ==============================================================================
# Production Profile: Enterprise MFT-to-MFT (IBM Sterling B2Bi / File Gateway)
# ==============================================================================
mft:
  scheduling:
    enabled: true
    fixed-delay-ms: 10000 # Poll every 10 seconds

  connections:
    # --------------------------------------------------------------------------
    # SOURCE MFT: IBM Sterling B2Bi / File Gateway
    # --------------------------------------------------------------------------
    C_IBM_SOURCE:
      connection-id: C_IBM_SOURCE
      connection-type: SFTP
      host: ${IBM_SOURCE_MFT_HOST:ibm-source.corp.internal}
      port: ${IBM_SOURCE_MFT_PORT:10022}
      username: ${IBM_SOURCE_MFT_USER:mft_bridge_in}
      authentication-type: ${IBM_SOURCE_AUTH_TYPE:PRIVATE_KEY}
      private-key-path: ${IBM_SOURCE_KEY_PATH:config/keys/ibm_mft_engine_rsa}
      private-key-passphrase: ${IBM_SOURCE_KEY_PASSPHRASE:}
      remote-base-directory: /Mailbox/PartnerA/upload
      host-key-verification-mode: STRICT_KNOWN_HOSTS
      known-hosts-file: config/keys/known_hosts
      connection-timeout-ms: 30000
      session-timeout-ms: 60000
      enabled: true

    # --------------------------------------------------------------------------
    # DESTINATION MFT: IBM Sterling B2Bi / File Gateway
    # --------------------------------------------------------------------------
    C_IBM_DEST:
      connection-id: C_IBM_DEST
      connection-type: SFTP
      host: ${IBM_DEST_MFT_HOST:ibm-dest.corp.internal}
      port: ${IBM_DEST_MFT_PORT:10022}
      username: ${IBM_DEST_MFT_USER:mft_bridge_out}
      authentication-type: ${IBM_DEST_AUTH_TYPE:PRIVATE_KEY}
      private-key-path: ${IBM_DEST_KEY_PATH:config/keys/ibm_mft_engine_rsa}
      private-key-passphrase: ${IBM_DEST_KEY_PASSPHRASE:}
      remote-base-directory: /Mailbox/CoreBanking/incoming
      host-key-verification-mode: STRICT_KNOWN_HOSTS
      known-hosts-file: config/keys/known_hosts
      connection-timeout-ms: 30000
      session-timeout-ms: 60000
      enabled: true

  routes:
    # Route 1: RAW Binary Sync with SHA-256 Deduplication
    R_IBM_RAW_COPY:
      route-id: R_IBM_RAW_COPY
      source-connection-id: C_IBM_SOURCE
      destination-connection-id: C_IBM_DEST
      processing-mode: RAW_COPY
      source-directory: /Mailbox/PartnerA/upload
      source-pattern: "*.txt"
      destination-directory: /Mailbox/CoreBanking/incoming
      checksum-algorithm: SHA_256
      retry-limit: 3
      allow-reprocessing: false
      enabled: true

    # Route 2: Financial Transformation (Fixed-Width -> CSV + Control Totals + Manifest)
    R_IBM_TRANSFORM:
      route-id: R_IBM_TRANSFORM
      source-connection-id: C_IBM_SOURCE
      destination-connection-id: C_IBM_DEST
      processing-mode: TRANSFORM
      source-directory: /Mailbox/PartnerA/upload
      source-pattern: "*.dat"
      destination-directory: /Mailbox/CoreBanking/incoming
      checksum-algorithm: SHA_256
      retry-limit: 3
      allow-reprocessing: false
      enabled: true
```

---

## 4. Production Security & Environment Variable Injection

Never hardcode credentials or production hostnames in plain text. Inject them at startup using system environment variables:

### Windows Server (PowerShell):
```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:IBM_SOURCE_MFT_HOST="10.150.20.10"
$env:IBM_SOURCE_MFT_PORT="10022"
$env:IBM_SOURCE_MFT_USER="mft_bridge_in"
$env:IBM_SOURCE_KEY_PATH="D:\mft\config\keys\ibm_mft_engine_rsa"

$env:IBM_DEST_MFT_HOST="10.150.20.20"
$env:IBM_DEST_MFT_PORT="10022"
$env:IBM_DEST_MFT_USER="mft_bridge_out"
$env:IBM_DEST_KEY_PATH="D:\mft\config\keys\ibm_mft_engine_rsa"

# Launch Engine in Production Mode
java -jar bin\mft-integration-engine-0.0.1-SNAPSHOT.jar
```

### Linux Enterprise Server (systemd service):
```ini
[Unit]
Description=Enterprise MFT Integration Engine
After=network.target

[Service]
Type=simple
User=mftservice
WorkingDirectory=/opt/mft-engine
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="IBM_SOURCE_MFT_HOST=10.150.20.10"
Environment="IBM_DEST_MFT_HOST=10.150.20.20"
ExecStart=/usr/bin/java -jar /opt/mft-engine/bin/mft-integration-engine-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

---

## 5. Network Firewall & Enterprise Port Requirements

Ensure firewall rules allow bi-directional traffic between the MFT Integration Engine and the IBM Sterling MFT servers:

| Source | Destination | Port | Protocol | Purpose |
|---|---|---|---|---|
| MFT Integration Engine | Source IBM Sterling MFT | `10022` / `22` | TCP (SFTP/SSH) | File discovery, streaming download, duplicate validation |
| MFT Integration Engine | Destination IBM Sterling MFT | `10022` / `22` | TCP (SFTP/SSH) | Two-phase atomic upload (`.part` -> final commit) |
| Enterprise Client Browsers | MFT Integration Engine | `8080` / `443` | TCP (HTTPS) | Web Control Dashboard, REST APIs, Audit Ledger |
| Prometheus / Monitoring | MFT Integration Engine | `8080` | TCP (HTTP) | Health check (`/api/health`) and metrics scraping |

> [!NOTE]
> If your enterprise utilizes **IBM Sterling Secure Proxy (SSP)** in the DMZ, configure the MFT Engine's `host` parameter to point to the **SSP Perimeter Server IP/DNS** instead of the internal B2Bi cluster. The engine automatically negotiates the connection through the proxy.

---

## 6. Verification & Go-Live Checklist

Once configured, verify the end-to-end integration:

1. **Verify Handshake via REST API:**
   Test connection to both IBM Sterling instances without waiting for a scheduled job:
   ```bash
   curl -X POST http://localhost:8080/api/connections/C_IBM_SOURCE/test
   curl -X POST http://localhost:8080/api/connections/C_IBM_DEST/test
   ```
   Both endpoints will return `{"success": true, "message": "Connection test successful"}`.

2. **Trigger Route Execution:**
   Drop a file into the Source IBM Sterling Mailbox (`/Mailbox/PartnerA/upload/trades.dat`), then trigger:
   ```bash
   curl -X POST http://localhost:8080/api/routes/R_IBM_TRANSFORM/execute
   ```

3. **Inspect Output on Destination IBM Sterling:**
   Log into Destination IBM Sterling File Gateway. Verify that the files appear in `/Mailbox/CoreBanking/incoming/`:
   - `POS_TRANSFORMED_<timestamp>.csv`
   - `POS_TRANSFORMED_<timestamp>.csv.sha256`
   - `POS_TRANSFORMED_<timestamp>.csv.manifest.json`

4. **Verify Zero Duplicate Re-Transfer:**
   Trigger the route a second time. The dashboard and audit ledger will display:
   `SKIPPED_DUPLICATE` (Checksum matches previously transferred file). Zero files re-sent.
