# Windows & SFTPGo Setup Guide

This guide describes how to configure, launch, and maintain the dual SFTPGo simulation environment on Windows PowerShell.

---

## 1. Directory Structure

The SFTPGo binaries and instance configurations are organized under `D:\sftpgo`:

```
D:\sftpgo\
|-- sftpgo.exe                   # SFTPGo v2.7.6 64-bit Windows binary
|-- start-server-a.ps1           # Source SFTP launcher
|-- start-server-b.ps1           # Destination SFTP launcher
|
|-- instance-a\                  # Source SFTP Server (Port 2022)
|   |-- config\
|   |   |-- sftpgo.json          # Server A configuration (SFTP port 2022, WebAdmin 9080)
|   |   |-- users_seed.json      # User mft_source credentials and permissions
|   |   |-- templates\email\     # Required email templates
|   |-- data\mft_source\upload\  # Physical source file drop folder
|
|-- instance-b\                  # Destination SFTP Server (Port 2222)
|   |-- config\
|   |   |-- sftpgo.json          # Server B configuration (SFTP port 2222, WebAdmin 9081)
|   |   |-- users_seed.json      # User mft_destination credentials and permissions
|   |   |-- templates\email\     # Required email templates
|   |-- data\mft_destination\incoming\ # Physical destination drop folder
```

---

## 2. Server Specifications

| Attribute | Server A (Source) | Server B (Destination) |
| :--- | :--- | :--- |
| **SFTP Endpoint** | `sftp://localhost:2022` | `sftp://localhost:2222` |
| **WebAdmin URL** | `http://localhost:9080/web/admin` | `http://localhost:9081/web/admin` |
| **Username** | `mft_source` | `mft_destination` |
| **Password** | `source_pass` | `dest_pass` |
| **Permissions** | `list`, `download`, `delete` | `*` (full read/write/rename) |
| **Physical Root** | `D:\sftpgo\instance-a\data\mft_source` | `D:\sftpgo\instance-b\data\mft_destination` |

---

## 3. Starting the Servers

Open two PowerShell windows:

```powershell
# Window 1: Server A
powershell -ExecutionPolicy Bypass -File "D:\sftpgo\start-server-a.ps1"

# Window 2: Server B
powershell -ExecutionPolicy Bypass -File "D:\sftpgo\start-server-b.ps1"
```

---

## 4. Troubleshooting Windows Path & Template Issues

If SFTPGo reports:
`error loading required template: reset-password.html: The system cannot find the path specified.`

Ensure that the `templates` directory from the SFTPGo distribution is copied into `instance-a\config\templates\` and `instance-b\config\templates\`, and that `sftpgo.json` specifies:
```json
"smtp": {
  "templates_path": "templates"
}
```
Relative to the `--config-dir` parameter supplied at startup.
