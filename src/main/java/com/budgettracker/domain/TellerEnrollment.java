package com.budgettracker.domain;

import java.time.Instant;
import java.util.UUID;

public record TellerEnrollment(
        UUID id,
        UUID householdId,
        String tellerId,
        String institution,
        String encryptedToken,
        Instant createdAt) {
}
