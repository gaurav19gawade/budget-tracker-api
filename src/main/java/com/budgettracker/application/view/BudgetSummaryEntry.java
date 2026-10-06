package com.budgettracker.application.view;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row in the monthly budget summary.
 * {@code categoryId == null} means the overall household total row.
 */
public record BudgetSummaryEntry(
        UUID categoryId,
        String categoryName,
        String categoryColor,
        String categoryIcon,
        BigDecimal budgeted,   // 0 if no budget set
        BigDecimal spent,      // absolute sum of negative (expense) transactions
        BigDecimal income,     // sum of positive (income) transactions
        BigDecimal available   // budgeted - spent (negative = over budget)
) {
}
