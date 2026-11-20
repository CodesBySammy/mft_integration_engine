package com.mft.integration.retry;

import com.mft.integration.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * Executes operations with exponential backoff retry and audit tracking.
 */
@Component
public class RetryManager {

    private static final Logger log = LoggerFactory.getLogger(RetryManager.class);

    private final AuditService auditService;

    public RetryManager(AuditService auditService) {
        this.auditService = auditService;
    }

    public <T> T execute(String operationName, String runId, String routeId, RetryPolicy policy, Callable<T> action) throws Exception {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return action.call();
            } catch (Exception e) {
                if (!policy.isRetryable(e)) {
                    log.warn("Non-retryable error in [{}] on attempt {}/{}: {}",
                            operationName, attempt, policy.getMaxAttempts(), e.getMessage());
                    throw e;
                }

                if (attempt >= policy.getMaxAttempts()) {
                    log.error("Operation [{}] permanently failed after {} attempts: {}",
                            operationName, attempt, e.getMessage());
                    throw e;
                }

                long delay = policy.calculateDelay(attempt);
                log.warn("Operation [{}] failed on attempt {}/{}: {}. Retrying in {}ms...",
                        operationName, attempt, policy.getMaxAttempts(), e.getMessage(), delay);

                if (auditService != null && runId != null) {
                    auditService.recordEvent(
                            runId,
                            routeId,
                            "RETRY_ATTEMPT",
                            operationName,
                            null,
                            "RETRY_PENDING",
                            "ATTEMPT_" + attempt,
                            String.format("Attempt %d/%d failed: %s. Retrying in %dms", attempt, policy.getMaxAttempts(), e.getMessage(), delay)
                    );
                }

                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Retry wait interrupted", ie);
                    }
                }
            }
        }
    }
}
