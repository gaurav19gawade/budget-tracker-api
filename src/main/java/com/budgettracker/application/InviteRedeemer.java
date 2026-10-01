package com.budgettracker.application;

import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.application.port.InviteRepository;
import com.budgettracker.application.port.TokenService;
import com.budgettracker.domain.Household;
import com.budgettracker.domain.HouseholdInvite;
import com.budgettracker.domain.error.ConflictException;
import com.budgettracker.domain.error.InvalidInviteException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns a valid invite token into household membership. */
@Service
public class InviteRedeemer {

    private final InviteRepository invites;
    private final HouseholdRepository households;
    private final TokenService tokens;
    private final Clock clock;

    public InviteRedeemer(InviteRepository invites, HouseholdRepository households,
                          TokenService tokens, Clock clock) {
        this.invites = invites;
        this.households = households;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public Household redeem(AuthenticatedPrincipal principal, String rawToken) {
        if (households.findHouseholdIdOfUser(principal.userId()).isPresent()) {
            throw new ConflictException("You already belong to a household.");
        }
        Instant now = clock.instant();
        HouseholdInvite invite = invites.findByTokenHash(tokens.hash(rawToken))
                .filter(i -> i.isRedeemable(now))
                .orElseThrow(InvalidInviteException::new);

        // Authoritative single-use check: one conditional UPDATE, so concurrent redemptions cannot both win.
        if (!invites.markUsed(invite.id(), principal.userId(), now)) {
            throw new InvalidInviteException();
        }
        households.addMember(invite.householdId(), principal.userId(), now);
        return households.findById(invite.householdId()).orElseThrow(InvalidInviteException::new);
    }
}
