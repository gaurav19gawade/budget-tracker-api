package com.budgettracker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Transaction(
        UUID id,
        UUID householdId,
        UUID accountId,
        String providerId,
        BigDecimal amount,
        String currency,
        String description,
        String payee,
        String memo,
        LocalDate postedDate,
        LocalDate transactedAt,
        boolean pending,
        boolean isInternalTransfer,
        UUID transferGroupId,
        Instant createdAt,
        Instant updatedAt) {
}
