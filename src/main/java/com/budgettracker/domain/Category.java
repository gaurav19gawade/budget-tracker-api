package com.budgettracker.domain;

import java.time.Instant;
import java.util.UUID;

public record Category(
        UUID id,
        UUID householdId,
        String name,
        String color,
        String icon,
        boolean isSystem,
        Instant createdAt) {
}
