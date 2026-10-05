package com.budgettracker.application.view;

import java.time.Instant;
import java.util.UUID;

public record CategoryView(
        UUID id,
        String name,
        String color,
        String icon,
        boolean isSystem,
        Instant createdAt) {
}
