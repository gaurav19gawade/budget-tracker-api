package com.budgettracker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BankAccount(
        UUID id,
        UUID householdId,
        UUID enrollmentId,
        String tellerId,
        String institution,
        String name,
        String type,
        String subtype,
        String lastFour,
        String currency,
        BigDecimal balanceAvailable,
        BigDecimal balanceLedger,
        Instant lastSyncedAt,
        String status,
        Instant createdAt,
        Instant removedAt) {
}
