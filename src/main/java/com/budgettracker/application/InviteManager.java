package com.budgettracker.application;

import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.port.InviteRepository;
import com.budgettracker.application.port.TokenService;
import com.budgettracker.application.view.CreatedInvite;
import com.budgettracker.domain.HouseholdInvite;
import com.budgettracker.domain.error.ConflictException;
import com.budgettracker.domain.error.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Create, list and revoke invites for the caller's own household. */
@Service
public class InviteManager {

    static final Duration TTL = Duration.ofDays(7);

    private final InviteRepository invites;
    private final TokenService tokens;
    private final Clock clock;

    public InviteManager(InviteRepository invites, TokenService tokens, Clock clock) {
        this.invites = invites;
        this.tokens = tokens;
        this.clock = clock;
    }

    /** The raw token is returned once here and cannot be recovered later (only its hash is stored). */
    public CreatedInvite create(HouseholdContext ctx) {
        Instant now = clock.instant();
        String rawToken = tokens.newToken();
        HouseholdInvite invite = new HouseholdInvite(
                UUID.randomUUID(), ctx.householdId(), tokens.hash(rawToken), ctx.userId(),
                now, now.plus(TTL), null, null, null);
        invites.save(invite);
        return new CreatedInvite(invite.id(), rawToken, invite.expiresAt());
    }

    public List<HouseholdInvite> list(HouseholdContext ctx) {
        return invites.findByHousehold(ctx.householdId());
    }

    public void revoke(HouseholdContext ctx, UUID inviteId) {
        HouseholdInvite invite = invites.findByIdAndHouseholdId(inviteId, ctx.householdId())
                .orElseThrow(() -> new NotFoundException("Invite not found."));
        if (invite.usedAt() != null) {
            throw new ConflictException("This invite has already been used.");
        }
        if (invite.revokedAt() == null) {
            invites.save(invite.revoke(clock.instant()));
        }
    }
}
