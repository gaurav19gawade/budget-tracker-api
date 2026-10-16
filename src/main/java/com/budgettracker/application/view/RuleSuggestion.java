package com.budgettracker.application.view;

import java.util.UUID;

public record RuleSuggestion(
        String payee,
        int matchCount,
        UUID categoryId,
        String categoryName,
        String categoryColor,
        String categoryIcon) {
}
