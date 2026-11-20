package com.mft.integration.processing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Parsed and validated trade position domain model.
 */
public record PositionRecord(
        String tradeId,
        String currency,
        BigDecimal amount,
        LocalDate businessDate
) {}
