package com.mft.integration.persistence;

import com.mft.integration.audit.AuditEvent;
import com.mft.integration.idempotency.IdempotencyRecord;
import com.mft.integration.idempotency.IdempotencyStatus;
import com.mft.integration.status.TransferRun;
import com.mft.integration.status.TransferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "mft.scheduling.enabled=false"
})
class DatabasePersistenceServiceTest {

    @Autowired
    private DatabasePersistenceService databasePersistenceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Persists audit events into relational database table mft_audit_events")
    void testSaveAuditEvent() {
        String runId = "TEST_RUN_DB_" + System.currentTimeMillis();
        AuditEvent event = new AuditEvent(
                Instant.now(),
                runId,
                "R_RAW_COPY",
                "C_FILE_IN -> C_FILE_OUT",
                "C_FILE_IN",
                "C_FILE_OUT",
                "localhost:2022/upload",
                "localhost:2222/incoming",
                "FILE_DOWNLOADED",
                "/upload/trade_db.txt",
                "/incoming/trade_db.txt",
                2048L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                45L,
                "COMPLETED",
                null,
                "Downloaded 2048 bytes from source"
        );

        databasePersistenceService.saveAuditEvent(event);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mft_audit_events WHERE run_id = ?",
                Integer.class,
                runId
        );
        assertThat(count).isGreaterThanOrEqualTo(1);

        String operation = jdbcTemplate.queryForObject(
                "SELECT operation FROM mft_audit_events WHERE run_id = ?",
                String.class,
                runId
        );
        assertThat(operation).isEqualTo("FILE_DOWNLOADED");
    }

    @Test
    @DisplayName("Persists and updates transfer runs into relational table mft_transfer_runs")
    void testSaveTransferRun() {
        String runId = "RUN_DB_LIFECYCLE_" + System.currentTimeMillis();
        TransferRun run = new TransferRun(runId, "R_RAW_COPY", "C_FILE_IN", "C_FILE_OUT", "sample.txt");
        run.setStatus(TransferStatus.PROCESSING);
        run.setBytesTransferred(1024L);

        databasePersistenceService.saveTransferRun(run);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM mft_transfer_runs WHERE run_id = ?",
                String.class,
                runId
        );
        assertThat(status).isEqualTo("PROCESSING");

        // Update status to COMPLETED
        run.setStatus(TransferStatus.COMPLETED);
        run.setOutputChecksum("hash12345");
        databasePersistenceService.saveTransferRun(run);

        String updatedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM mft_transfer_runs WHERE run_id = ?",
                String.class,
                runId
        );
        assertThat(updatedStatus).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Persists idempotency records into relational table mft_idempotency_records")
    void testSaveIdempotencyRecord() {
        String hash = "hash_key_" + System.currentTimeMillis();
        IdempotencyRecord record = new IdempotencyRecord(
                hash,
                "R_RAW_COPY",
                "C_FILE_IN",
                "/upload/file.docx",
                5000L,
                "sha256_mock",
                "RUN_IDEMPOTENT_1",
                IdempotencyStatus.COMPLETED
        );
        record.setDestinationPath("/incoming/file.docx");

        databasePersistenceService.saveIdempotencyRecord(record);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mft_idempotency_records WHERE idempotency_key = ?",
                Integer.class,
                hash
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Reports healthy relational database status and row counts")
    void testDatabaseStatus() {
        Map<String, Object> status = databasePersistenceService.getDatabaseStatus();
        assertThat(status.get("databaseConnected")).isEqualTo(true);
        assertThat(status.get("productName")).isNotNull();
        assertThat((Integer) status.get("auditEventsCount")).isGreaterThanOrEqualTo(0);
    }
}
