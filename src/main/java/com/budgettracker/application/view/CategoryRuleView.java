package com.budgettracker.application.view;

import com.budgettracker.domain.CategoryRule;
import java.time.Instant;
import java.util.UUID;

public record CategoryRuleView(
        UUID id,
        UUID categoryId,
        int priority,
        CategoryRule.MatchField matchField,
        String matchValue,
        Instant createdAt) {
}
