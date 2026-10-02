package com.budgettracker.application.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountView(
        UUID id,
        String institution,
        String name,
        String type,
        String subtype,
        String lastFour,
        String currency,
        BigDecimal balanceAvailable,
        BigDecimal balanceLedger,
        Instant lastSyncedAt) {
}
