package com.budgettracker.application;

import com.budgettracker.domain.CategoryRule;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stateless rules engine.  Given a list of rules (sorted by priority ascending) and
 * the relevant fields of a transaction, returns the category ID of the first matching
 * rule, or {@code null} if no rule matches.
 */
@Component
public class Categorizer {

    public UUID categorize(String payee, String description, BigDecimal amount,
                           UUID accountId, List<CategoryRule> rules) {
        for (CategoryRule rule : rules) {
            if (matches(payee, description, amount, accountId, rule)) {
                return rule.categoryId();
            }
        }
        return null;
    }

    private boolean matches(String payee, String description, BigDecimal amount,
                            UUID accountId, CategoryRule rule) {
        return switch (rule.matchField()) {
            case PAYEE_CONTAINS -> containsIgnoreCase(payee, rule.matchValue());
            case DESCRIPTION_CONTAINS -> containsIgnoreCase(description, rule.matchValue());
            case AMOUNT_GTE -> amount != null
                    && amount.compareTo(new BigDecimal(rule.matchValue())) >= 0;
            case AMOUNT_LTE -> amount != null
                    && amount.compareTo(new BigDecimal(rule.matchValue())) <= 0;
            case ACCOUNT_ID -> accountId != null
                    && accountId.toString().equals(rule.matchValue());
        };
    }

    private static boolean containsIgnoreCase(String field, String value) {
        return field != null && field.toLowerCase().contains(value.toLowerCase());
    }
}
