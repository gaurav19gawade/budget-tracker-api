package com.budgettracker.domain;

import java.time.Instant;
import java.util.UUID;

public record CategoryRule(
        UUID id,
        UUID householdId,
        UUID categoryId,
        int priority,
        MatchField matchField,
        String matchValue,
        Instant createdAt) {

    public enum MatchField {
        PAYEE_CONTAINS,
        DESCRIPTION_CONTAINS,
        AMOUNT_GTE,
        AMOUNT_LTE,
        ACCOUNT_ID
    }
}
