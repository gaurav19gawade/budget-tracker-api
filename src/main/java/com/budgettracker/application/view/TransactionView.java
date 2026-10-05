package com.budgettracker.application.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionView(
        UUID id,
        UUID accountId,
        BigDecimal amount,
        String currency,
        String description,
        String payee,
        String memo,
        LocalDate postedDate,
        LocalDate transactedAt,
        boolean pending,
        boolean isInternalTransfer,
        UUID categoryId,
        boolean categoryOverride,
        Instant createdAt) {
}
