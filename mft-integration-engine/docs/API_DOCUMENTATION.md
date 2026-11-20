# MFT Integration Engine REST API Documentation

The MFT Integration Engine provides a RESTful management API for monitoring transfer runs, testing SFTP connectivity, discovering remote files, triggering routes, and reviewing structured audit logs.

All API responses follow a uniform envelope format:
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2026-11-20T09:50:00Z"
}
```

All error responses adhere to standard error envelopes:
```json
{
  "success": false,
  "errorCode": "CONNECTION_NOT_FOUND",
  "errorMessage": "Connection definition not found for id: UNKNOWN",
  "path": "/api/connections/UNKNOWN",
  "timestamp": "2026-11-20T09:50:00Z"
}
```

---

## 1. System Health

### 1.1 `GET /api/health`
Returns the status of the integration engine, local workspace directory, and counts of active routes and connections.

**PowerShell Example:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get | ConvertTo-Json -Depth 5
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "System healthy",
  "data": {
    "status": "UP",
    "applicationName": "mft-integration-engine",
    "activeConnections": 2,
    "activeRoutes": 2,
    "workspaceReady": true,
    "timestamp": "2026-11-20T09:50:00Z",
    "details": {
      "workspaceRoot": "D:\\mft\\workspace",
      "configuredConnections": 2,
      "configuredRoutes": 2,
      "status": "UP"
    }
  },
  "timestamp": "2026-11-20T09:50:00Z"
}
```

### 1.2 `GET /actuator/health`
Standard Spring Boot Actuator health endpoint.

---

## 2. Connection Management

### 2.1 `GET /api/connections`
Lists all registered SFTP connection definitions. Passwords and credentials are never serialized or returned.

**PowerShell Example:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/connections" -Method Get | ConvertTo-Json -Depth 5
```

### 2.2 `GET /api/connections/{connectionId}`
Retrieves configuration for a specific connection ID (`C_FILE_IN`, `C_FILE_OUT`).

### 2.3 `GET /api/connections/{connectionId}/test`
Tests live SFTP reachability by opening a session through Apache MINA SSHD and measuring round-trip latency.

**PowerShell Example:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/connections/C_FILE_IN/test" -Method Get | ConvertTo-Json -Depth 5
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "connectionId": "C_FILE_IN",
    "host": "localhost",
    "port": 2022,
    "reachable": true,
    "responseTimeMs": 18,
    "message": "Successfully connected to SFTP endpoint localhost:2022 in 18 ms"
  },
  "timestamp": "2026-11-20T09:50:00Z"
}
```

---

## 3. Remote File Operations

### 3.1 `GET /api/files/{connectionId}?directory=/upload&pattern=*`
Discovers and lists remote files on the specified SFTP connection without downloading them.

**PowerShell Example:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/files/C_FILE_IN?directory=/upload&pattern=*.txt" -Method Get | ConvertTo-Json -Depth 5
```

### 3.2 `POST /api/files/download`
Downloads a remote file from an SFTP endpoint directly into the local workspace downloads folder.

**Parameters (Query or Form):**
- `connectionId`: e.g. `C_FILE_IN`
- `remotePath`: e.g. `/upload/trades.txt`
- `targetFilename` (optional): custom local filename

---

## 4. Route Execution

### 4.1 `GET /api/routes`
Lists all registered integration routes (`R_RAW_COPY`, `R_TRANSFORM`).

### 4.2 `GET /api/routes/{routeId}`
Retrieves the route definition, processing mode, directory paths, and retry configuration.

### 4.3 `POST /api/routes/{routeId}/execute`
Triggers immediate execution of the route. Supports optional pattern override in the JSON payload.

**PowerShell Example (Raw Copy):**
```powershell
$body = @{ patternOverride = "positions.txt" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/routes/R_RAW_COPY/execute" -Method Post -Body $body -ContentType "application/json" | ConvertTo-Json -Depth 5
```

**PowerShell Example (Transformation):**
```powershell
$body = @{ patternOverride = "trade_positions_*.dat" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/routes/R_TRANSFORM/execute" -Method Post -Body $body -ContentType "application/json" | ConvertTo-Json -Depth 5
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Route executed successfully: Completed route [R_RAW_COPY]. Discovered: 1, Transferred: 1, Skipped: 0, Failed: 0",
  "data": {
    "runId": "R_RAW_COPY_20261120_095000_a1b2c3d4",
    "routeId": "R_RAW_COPY",
    "success": true,
    "filesDiscovered": 1,
    "filesTransferred": 1,
    "filesSkipped": 0,
    "filesFailed": 0,
    "startTime": "2026-11-20T09:50:00.123Z",
    "endTime": "2026-11-20T09:50:00.456Z",
    "message": "Completed route [R_RAW_COPY]. Discovered: 1, Transferred: 1, Skipped: 0, Failed: 0",
    "itemRuns": [
      {
        "runId": "R_RAW_COPY_20261120_095000_a1b2c3d4-positions.txt",
        "routeId": "R_RAW_COPY",
        "sourceConnectionId": "C_FILE_IN",
        "destinationConnectionId": "C_FILE_OUT",
        "sourceFilename": "positions.txt",
        "destinationFilename": "positions.txt",
        "status": "COMPLETED",
        "bytesTransferred": 2048,
        "recordCount": 0,
        "sourceChecksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "outputChecksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "startTime": "2026-11-20T09:50:00.125Z",
        "endTime": "2026-11-20T09:50:00.450Z"
      }
    ]
  },
  "timestamp": "2026-11-20T09:50:00Z"
}
```

---

## 5. Transfer Lifecycle & Audit Monitoring

### 5.1 `GET /api/transfers`
Lists all transfer runs, with optional filtering by route: `GET /api/transfers?routeId=R_RAW_COPY`.

### 5.2 `GET /api/transfers/{runId}`
Retrieves lifecycle status, byte counts, and checksums for a specific transfer execution run.

### 5.3 `GET /api/transfers/{runId}/audit`
Retrieves chronological audit events specifically associated with that transfer run.

### 5.4 `GET /api/audit`
Lists all audit events across all transfers, filterable by `routeId` or `runId`:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/audit?routeId=R_RAW_COPY" -Method Get | ConvertTo-Json -Depth 5
```

---

## 6. HTTP Status Code Mapping

| Status Code | Description | Example Cause |
| :--- | :--- | :--- |
| `200 OK` | Request completed successfully. | Route executed, connection tested. |
| `400 Bad Request` | Missing or invalid request parameters. | Invalid directory syntax, illegal path traversal (`..`). |
| `404 Not Found` | Target resource does not exist. | Route or connection ID does not exist in registry. |
| `422 Unprocessable Entity` | Business logic or data integrity rejection. | SHA-256 checksum mismatch, layout parser validation failure. |
| `503 Service Unavailable` | Downstream SFTP failure. | SFTP endpoint host or port unreachable. |
| `500 Internal Server Error` | Unexpected system failure. | Unhandled exception (sanitized in response). |
