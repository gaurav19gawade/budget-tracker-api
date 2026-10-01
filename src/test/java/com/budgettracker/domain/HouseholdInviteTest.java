package com.budgettracker.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HouseholdInviteTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private HouseholdInvite invite(Instant expiresAt, Instant usedAt, Instant revokedAt) {
        return new HouseholdInvite(UUID.randomUUID(), UUID.randomUUID(), "hash", UUID.randomUUID(),
                NOW.minus(Duration.ofDays(1)), expiresAt, usedAt, usedAt == null ? null : UUID.randomUUID(), revokedAt);
    }

    @Test
    void freshInviteIsActiveAndRedeemable() {
        HouseholdInvite invite = invite(NOW.plus(Duration.ofDays(1)), null, null);
        assertEquals(HouseholdInvite.Status.ACTIVE, invite.statusAt(NOW));
        assertTrue(invite.isRedeemable(NOW));
    }

    @Test
    void inviteExpiresExactlyAtItsExpiryInstant() {
        HouseholdInvite invite = invite(NOW, null, null);
        assertEquals(HouseholdInvite.Status.EXPIRED, invite.statusAt(NOW));
        assertFalse(invite.isRedeemable(NOW));
        assertTrue(invite.isRedeemable(NOW.minusSeconds(1)));
    }

    @Test
    void usedAndRevokedInvitesAreNotRedeemable() {
        assertEquals(HouseholdInvite.Status.USED, invite(NOW.plusSeconds(60), NOW, null).statusAt(NOW));
        assertEquals(HouseholdInvite.Status.REVOKED, invite(NOW.plusSeconds(60), null, NOW).statusAt(NOW));
        assertFalse(invite(NOW.plusSeconds(60), NOW, null).isRedeemable(NOW));
        assertFalse(invite(NOW.plusSeconds(60), null, NOW).isRedeemable(NOW));
    }

    @Test
    void revokeKeepsEverythingElse() {
        HouseholdInvite original = invite(NOW.plusSeconds(60), null, null);
        HouseholdInvite revoked = original.revoke(NOW);
        assertEquals(original.id(), revoked.id());
        assertEquals(original.tokenHash(), revoked.tokenHash());
        assertEquals(NOW, revoked.revokedAt());
    }
}
