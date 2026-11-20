package com.mft.integration.audit;

import com.mft.integration.status.InMemoryTransferStatusRepository;
import com.mft.integration.status.TransferRun;
import com.mft.integration.status.TransferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAndStatusRepositoryTest {

    @Test
    @DisplayName("Stores and queries AuditEvents by runId and routeId")
    void testAuditRepositoryOperations() {
        InMemoryAuditRepository repo = new InMemoryAuditRepository();

        AuditEvent e1 = new AuditEvent("RUN_1", "R_RAW", "DOWNLOAD", "/src/a.txt", null, "COMPLETED", null, null);
        AuditEvent e2 = new AuditEvent("RUN_1", "R_RAW", "UPLOAD", null, "/dst/a.txt", "COMPLETED", null, null);
        AuditEvent e3 = new AuditEvent("RUN_2", "R_TX", "TRANSFORM", "/src/b.dat", "/dst/b.csv", "COMPLETED", null, null);

        repo.save(e1);
        repo.save(e2);
        repo.save(e3);

        List<AuditEvent> run1Events = repo.findByRunId("RUN_1");
        assertThat(run1Events).hasSize(2);

        List<AuditEvent> routeTxEvents = repo.findByRouteId("R_TX");
        assertThat(routeTxEvents).hasSize(1);
        assertThat(routeTxEvents.get(0).getOperation()).isEqualTo("TRANSFORM");

        assertThat(repo.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("Stores, queries, and updates TransferRun in status repository")
    void testTransferStatusRepositoryOperations() {
        InMemoryTransferStatusRepository repo = new InMemoryTransferStatusRepository();

        TransferRun run = new TransferRun("RUN_100", "R_RAW", "C_IN", "C_OUT", "trades.txt");
        repo.save(run);

        Optional<TransferRun> retrieved = repo.findById("RUN_100");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(TransferStatus.DISCOVERED);

        run.setStatus(TransferStatus.COMPLETED);
        repo.save(run);

        Optional<TransferRun> updated = repo.findById("RUN_100");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(TransferStatus.COMPLETED);

        assertThat(repo.findByRouteId("R_RAW")).hasSize(1);
        assertThat(repo.findByRouteId("NON_EXISTENT")).isEmpty();
    }
}
