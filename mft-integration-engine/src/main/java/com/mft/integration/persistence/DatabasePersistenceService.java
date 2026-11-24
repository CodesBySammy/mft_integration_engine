package com.mft.integration.persistence;

import com.mft.integration.audit.AuditEvent;
import com.mft.integration.idempotency.IdempotencyRecord;
import com.mft.integration.status.TransferRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enterprise Relational Database Persistence Service.
 * Persists audit logs, transfer runs, and idempotency states into relational tables
 * (H2 file-based local DB or MySQL) alongside local JSON files.
 */
@Service
public class DatabasePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(DatabasePersistenceService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Autowired(required = false)
    public DatabasePersistenceService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        if (jdbcTemplate != null) {
            log.info("Relational database logging initialized successfully with active DataSource.");
        } else {
            log.info("No relational DataSource configured; operating in file-based storage mode.");
        }
    }

    public boolean isDatabaseEnabled() {
        return jdbcTemplate != null;
    }

    public void saveAuditEvent(AuditEvent event) {
        if (jdbcTemplate == null || event == null) return;
        try {
            String sql = """
                INSERT INTO mft_audit_events (
                    event_timestamp, run_id, route_id, connection_id,
                    source_connection_id, destination_connection_id,
                    source_address, destination_address,
                    operation, source_file, destination_file,
                    file_size, checksum, duration_ms,
                    status, error_code, details
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            jdbcTemplate.update(sql,
                    event.getTimestamp() != null ? Timestamp.from(event.getTimestamp()) : new Timestamp(System.currentTimeMillis()),
                    event.getRunId(),
                    event.getRouteId(),
                    event.getConnectionId(),
                    event.getSourceConnectionId(),
                    event.getDestinationConnectionId(),
                    event.getSourceAddress(),
                    event.getDestinationAddress(),
                    event.getOperation(),
                    event.getSourceFile(),
                    event.getDestinationFile(),
                    event.getFileSize(),
                    event.getChecksum(),
                    event.getDurationMs(),
                    event.getStatus(),
                    event.getErrorCode(),
                    event.getDetails()
            );
        } catch (Exception e) {
            log.warn("Failed to persist audit event to relational DB: {}", e.getMessage());
        }
    }

    public void saveTransferRun(TransferRun run) {
        if (jdbcTemplate == null || run == null) return;
        try {
            // Check if exists to perform insert or update
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mft_transfer_runs WHERE run_id = ?",
                    Integer.class,
                    run.getRunId()
            );

            if (count != null && count > 0) {
                String updateSql = """
                    UPDATE mft_transfer_runs SET
                        status = ?, source_checksum = ?, output_checksum = ?,
                        bytes_transferred = ?, record_count = ?, end_time = ?,
                        error_code = ?, error_message = ?
                    WHERE run_id = ?
                    """;
                jdbcTemplate.update(updateSql,
                        run.getStatus() != null ? run.getStatus().name() : null,
                        run.getSourceChecksum(),
                        run.getOutputChecksum(),
                        run.getBytesTransferred(),
                        run.getRecordCount(),
                        run.getEndTime() != null ? Timestamp.from(run.getEndTime()) : null,
                        run.getErrorCode(),
                        run.getErrorMessage(),
                        run.getRunId()
                );
            } else {
                String insertSql = """
                    INSERT INTO mft_transfer_runs (
                        run_id, route_id, source_connection_id, destination_connection_id,
                        source_filename, destination_filename, status,
                        source_checksum, output_checksum, bytes_transferred,
                        record_count, start_time, end_time, error_code, error_message
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                jdbcTemplate.update(insertSql,
                        run.getRunId(),
                        run.getRouteId(),
                        run.getSourceConnection(),
                        run.getDestinationConnection(),
                        run.getSourceFilename(),
                        run.getDestinationFilename(),
                        run.getStatus() != null ? run.getStatus().name() : null,
                        run.getSourceChecksum(),
                        run.getOutputChecksum(),
                        run.getBytesTransferred(),
                        run.getRecordCount(),
                        run.getStartTime() != null ? Timestamp.from(run.getStartTime()) : new Timestamp(System.currentTimeMillis()),
                        run.getEndTime() != null ? Timestamp.from(run.getEndTime()) : null,
                        run.getErrorCode(),
                        run.getErrorMessage()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to persist transfer run [{}] to relational DB: {}", run.getRunId(), e.getMessage());
        }
    }

    public void saveIdempotencyRecord(IdempotencyRecord record) {
        if (jdbcTemplate == null || record == null) return;
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mft_idempotency_records WHERE idempotency_key = ?",
                    Integer.class,
                    record.getKey()
            );

            if (count != null && count > 0) {
                String updateSql = """
                    UPDATE mft_idempotency_records SET
                        status = ?, completed_at = ?, destination_path = ?
                    WHERE idempotency_key = ?
                    """;
                jdbcTemplate.update(updateSql,
                        record.getStatus() != null ? record.getStatus().name() : null,
                        record.getUpdatedAt() != null ? Timestamp.from(record.getUpdatedAt()) : null,
                        record.getDestinationPath(),
                        record.getKey()
                );
            } else {
                String insertSql = """
                    INSERT INTO mft_idempotency_records (
                        idempotency_key, route_id, source_connection_id, remote_path,
                        file_size, source_checksum, run_id, status,
                        first_processed_at, completed_at, destination_path
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                jdbcTemplate.update(insertSql,
                        record.getKey(),
                        record.getRouteId(),
                        record.getSourceConnectionId(),
                        record.getSourceFilePath(),
                        record.getFileSize(),
                        record.getChecksum(),
                        record.getRunId(),
                        record.getStatus() != null ? record.getStatus().name() : null,
                        record.getCreatedAt() != null ? Timestamp.from(record.getCreatedAt()) : new Timestamp(System.currentTimeMillis()),
                        record.getUpdatedAt() != null ? Timestamp.from(record.getUpdatedAt()) : null,
                        record.getDestinationPath()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to persist idempotency record [{}] to relational DB: {}", record.getKey(), e.getMessage());
        }
    }

    public Map<String, Object> getDatabaseStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        if (jdbcTemplate == null || dataSource == null) {
            status.put("databaseConnected", false);
            status.put("mode", "FILE_ONLY");
            status.put("message", "Relational database is not active; using local JSON file repositories.");
            return status;
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            status.put("databaseConnected", true);
            status.put("productName", meta.getDatabaseProductName());
            status.put("productVersion", meta.getDatabaseProductVersion());
            status.put("jdbcDriver", meta.getDriverName());
            status.put("url", meta.getURL());

            Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mft_audit_events", Integer.class);
            Integer runsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mft_transfer_runs", Integer.class);
            Integer idempCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mft_idempotency_records", Integer.class);

            status.put("auditEventsCount", auditCount != null ? auditCount : 0);
            status.put("transferRunsCount", runsCount != null ? runsCount : 0);
            status.put("idempotencyRecordsCount", idempCount != null ? idempCount : 0);
        } catch (Exception e) {
            status.put("databaseConnected", false);
            status.put("error", e.getMessage());
        }
        return status;
    }

    public java.util.List<Map<String, Object>> queryAuditEvents(int limit) {
        if (jdbcTemplate == null) return java.util.List.of();
        int safeLimit = Math.min(Math.max(1, limit), 1000);
        return jdbcTemplate.queryForList("SELECT * FROM mft_audit_events ORDER BY id DESC LIMIT ?", safeLimit);
    }

    public java.util.List<Map<String, Object>> queryTransferRuns(int limit) {
        if (jdbcTemplate == null) return java.util.List.of();
        int safeLimit = Math.min(Math.max(1, limit), 1000);
        return jdbcTemplate.queryForList("SELECT * FROM mft_transfer_runs ORDER BY start_time DESC LIMIT ?", safeLimit);
    }

    public java.util.List<Map<String, Object>> queryIdempotencyRecords(int limit) {
        if (jdbcTemplate == null) return java.util.List.of();
        int safeLimit = Math.min(Math.max(1, limit), 1000);
        return jdbcTemplate.queryForList("SELECT * FROM mft_idempotency_records ORDER BY first_processed_at DESC LIMIT ?", safeLimit);
    }
}
