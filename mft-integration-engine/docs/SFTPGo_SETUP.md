# Complete SFTPGo Setup Guide for Windows (Dual Instance Topology)

This guide provides the complete, exact steps for setting up, running, and verifying two real, isolated SFTPGo SFTP servers on Windows:
- **Server A (Source SFTP):** SFTP port `2022`, WebAdmin port `9080`
- **Server B (Destination SFTP):** SFTP port `2222`, WebAdmin port `9081`

The complete environment has been prepared in `D:\sftpgo`.

---

## 1. Directory Structure

The setup in `D:\sftpgo` has the following layout:

```text
D:\sftpgo\
├── sftpgo.exe                       <-- Official portable SFTPGo executable (v2.7.6)
├── start-server-a.ps1               <-- PowerShell launcher for Server A (Source)
├── start-server-b.ps1               <-- PowerShell launcher for Server B (Destination)
│
├── instance-a/                      <-- SERVER A (Source SFTP)
│   ├── config/
│   │   ├── sftpgo.json              <-- Server A configuration (SFTP: 2022, HTTP: 9080)
│   │   └── users_seed.json          <-- Pre-seeded user: mft_source / source_pass
│   ├── data/
│   │   └── mft_source/
│   │       └── upload/              <-- Virtual home folder containing positions.txt
│   └── sftpgo.db                    <-- Independent SQLite database
│
└── instance-b/                      <-- SERVER B (Destination SFTP)
    ├── config/
    │   ├── sftpgo.json              <-- Server B configuration (SFTP: 2222, HTTP: 9081)
    │   └── users_seed.json          <-- Pre-seeded user: mft_destination / dest_pass
    ├── data/
    │   └── mft_destination/
    │       └── incoming/            <-- Destination upload folder
    └── sftpgo.db                    <-- Independent SQLite database
```

---

## 2. Configuration Files

### Server A Configuration: [D:\sftpgo\instance-a\config\sftpgo.json](file:///D:/sftpgo/instance-a/config/sftpgo.json)
```json
{
  "common": {
    "idle_timeout": 15
  },
  "sftpd": {
    "bindings": [
      {
        "port": 2022,
        "address": "0.0.0.0"
      }
    ]
  },
  "httpd": {
    "bindings": [
      {
        "port": 9080,
        "address": "0.0.0.0",
        "enable_web_admin": true,
        "enable_web_client": true,
        "enable_rest_api": true
      }
    ],
    "templates_path": "D:\\sftpgo\\templates",
    "static_files_path": "D:\\sftpgo\\static",
    "openapi_path": "D:\\sftpgo\\openapi"
  },
  "data_provider": {
    "driver": "sqlite",
    "name": "D:\\sftpgo\\instance-a\\sftpgo.db"
  }
}
```

### Server B Configuration: [D:\sftpgo\instance-b\config\sftpgo.json](file:///D:/sftpgo/instance-b/config/sftpgo.json)
```json
{
  "common": {
    "idle_timeout": 15
  },
  "sftpd": {
    "bindings": [
      {
        "port": 2222,
        "address": "0.0.0.0"
      }
    ]
  },
  "httpd": {
    "bindings": [
      {
        "port": 9081,
        "address": "0.0.0.0",
        "enable_web_admin": true,
        "enable_web_client": true,
        "enable_rest_api": true
      }
    ],
    "templates_path": "D:\\sftpgo\\templates",
    "static_files_path": "D:\\sftpgo\\static",
    "openapi_path": "D:\\sftpgo\\openapi"
  },
  "data_provider": {
    "driver": "sqlite",
    "name": "D:\\sftpgo\\instance-b\\sftpgo.db"
  }
}
```

---

## 3. How to Start the Two SFTPGo Servers

You can start each server in its own PowerShell window:

### Step 3.1: Start Server A (Source)
Open a Windows PowerShell terminal and run:
```powershell
powershell -ExecutionPolicy Bypass -File "D:\sftpgo\start-server-a.ps1"
```
Or directly:
```powershell
Set-Location "D:\sftpgo"
.\sftpgo.exe serve --config-dir "D:\sftpgo\instance-a\config" --loaddata-from "D:\sftpgo\instance-a\config\users_seed.json" --loaddata-mode 0
```
- **SFTP Endpoint:** `sftp://localhost:2022`
- **WebAdmin URL:** `http://localhost:9080/web/admin`
- **Pre-configured User:** `mft_source`
- **Pre-configured Password:** `source_pass`

### Step 3.2: Start Server B (Destination)
Open a second Windows PowerShell terminal and run:
```powershell
powershell -ExecutionPolicy Bypass -File "D:\sftpgo\start-server-b.ps1"
```
Or directly:
```powershell
Set-Location "D:\sftpgo"
.\sftpgo.exe serve --config-dir "D:\sftpgo\instance-b\config" --loaddata-from "D:\sftpgo\instance-b\config\users_seed.json" --loaddata-mode 0
```
- **SFTP Endpoint:** `sftp://localhost:2222`
- **WebAdmin URL:** `http://localhost:9081/web/admin`
- **Pre-configured User:** `mft_destination`
- **Pre-configured Password:** `dest_pass`

---

## 4. How to Inspect or Manage Users via WebAdmin (Optional)

If you prefer to view or edit users using the SFTPGo WebAdmin UI:

1. Open your browser to `http://localhost:9080/web/admin` (Server A) or `http://localhost:9081/web/admin` (Server B).
2. On the first visit, SFTPGo will ask you to create a Web Admin account (e.g. username `admin`, password `AdminSecretPass123!`).
3. Click on **Users** in the left navigation sidebar.
4. You will see the pre-seeded user (`mft_source` on Server A, `mft_destination` on Server B).
5. Permissions configured:
   - **Server A (`mft_source`):** `list`, `download`, `delete`
   - **Server B (`mft_destination`):** `list`, `download`, `upload`, `overwrite`, `rename`, `create_dirs`, `delete`

---

## 5. How to Test Connectivity from PowerShell

In a third PowerShell window, test that both SFTP ports are actively accepting connections:

```powershell
Test-NetConnection -ComputerName localhost -Port 2022
Test-NetConnection -ComputerName localhost -Port 2222
```

Both should return: `TcpTestSucceeded : True`.

You can also test directly with Windows OpenSSH client:
```powershell
# Connect to Source
sftp -P 2022 mft_source@localhost
# (Enter password: source_pass)
# In SFTP prompt, type: ls -la

# Connect to Destination
sftp -P 2222 mft_destination@localhost
# (Enter password: dest_pass)
# In SFTP prompt, type: ls -la
```
