package com.mft.integration.retry;

import com.mft.integration.audit.AuditService;
import com.mft.integration.audit.InMemoryAuditRepository;
import com.mft.integration.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryManagerTest {

    private AuditService auditService;
    private RetryManager retryManager;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(new InMemoryAuditRepository());
        retryManager = new RetryManager(auditService);
    }

    @Test
    @DisplayName("Succeeds on first attempt without retrying")
    void testSuccessfulExecutionNoRetry() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, 10L, 2.0, 100L, 0.0);

        String result = retryManager.execute("TEST_OP", "RUN_1", "ROUTE_1", policy, () -> "SUCCESS");

        assertThat(result).isEqualTo("SUCCESS");
        assertThat(auditService.getAllEvents()).isEmpty();
    }

    @Test
    @DisplayName("Retries transient I/O exceptions and succeeds on second attempt")
    void testTransientErrorRetriedAndSucceeds() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, 10L, 1.5, 100L, 0.0);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = retryManager.execute("TEST_OP", "RUN_1", "ROUTE_1", policy, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("Connection reset by peer");
            }
            return "RECOVERED";
        });

        assertThat(result).isEqualTo("RECOVERED");
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(auditService.getAllEvents()).hasSize(1);
        assertThat(auditService.getAllEvents().get(0).getOperation()).isEqualTo("RETRY_ATTEMPT");
    }

    @Test
    @DisplayName("Fails fast on non-retryable domain validation error without retrying")
    void testNonRetryableValidationErrorFailsImmediately() {
        RetryPolicy policy = new RetryPolicy(3, 10L, 2.0, 100L, 0.0);
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryManager.execute("TEST_OP", "RUN_1", "ROUTE_1", policy, () -> {
            attempts.incrementAndGet();
            throw new ValidationException("INVALID_RECORD", "Record rejected by layout validation");
        })).isInstanceOf(ValidationException.class);

        assertThat(attempts.get()).isEqualTo(1); // Exactly 1 attempt, zero retries
        assertThat(auditService.getAllEvents()).isEmpty();
    }

    @Test
    @DisplayName("Exhausts retries and throws exception after maximum attempts")
    void testExhaustsMaxAttempts() {
        RetryPolicy policy = new RetryPolicy(3, 10L, 1.0, 100L, 0.0);
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryManager.execute("TEST_OP", "RUN_1", "ROUTE_1", policy, () -> {
            attempts.incrementAndGet();
            throw new IOException("Host unreachable");
        })).isInstanceOf(IOException.class);

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(auditService.getAllEvents()).hasSize(2); // Retries logged on attempt 1 and 2
    }
}
