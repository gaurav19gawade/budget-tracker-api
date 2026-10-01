package com.budgettracker.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use invitation to join a household. Only the hash of the token is kept.
 */
public record HouseholdInvite(
        UUID id,
        UUID householdId,
        String tokenHash,
        UUID createdBy,
        Instant createdAt,
        Instant expiresAt,
        Instant usedAt,
        UUID usedBy,
        Instant revokedAt) {

    public enum Status { ACTIVE, USED, REVOKED, EXPIRED }

    public Status statusAt(Instant now) {
        if (usedAt != null) {
            return Status.USED;
        }
        if (revokedAt != null) {
            return Status.REVOKED;
        }
        if (!expiresAt.isAfter(now)) {
            return Status.EXPIRED;
        }
        return Status.ACTIVE;
    }

    public boolean isRedeemable(Instant now) {
        return statusAt(now) == Status.ACTIVE;
    }

    public HouseholdInvite revoke(Instant now) {
        return new HouseholdInvite(id, householdId, tokenHash, createdBy, createdAt, expiresAt, usedAt, usedBy, now);
    }
}
