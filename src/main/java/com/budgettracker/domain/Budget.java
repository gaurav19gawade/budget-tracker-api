package com.budgettracker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A household's budget for one category in one calendar month.
 * {@code categoryId == null} represents an optional overall household spending cap.
 * {@code month} is always the first day of the month (e.g. 2026-10-01).
 */
public record Budget(
        UUID id,
        UUID householdId,
        UUID categoryId,
        LocalDate month,
        BigDecimal amount,
        Instant createdAt) {
}
