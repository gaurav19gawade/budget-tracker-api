package com.budgettracker.application;

import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.domain.Household;
import com.budgettracker.domain.error.ConflictException;
import com.budgettracker.domain.error.ForbiddenException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the very first household. Only the configured owner (by Supabase user id, not by
 * email, because unconfirmed emails can be claimed by anyone) may do this, and only while
 * no household exists. Everyone else joins through an invite.
 */
@Service
public class HouseholdBootstrapper {

    private final HouseholdRepository households;
    private final CategoryService categoryService;
    private final Clock clock;
    private final String ownerUserId;

    public HouseholdBootstrapper(HouseholdRepository households,
                                 CategoryService categoryService,
                                 Clock clock,
                                 @Value("${app.bootstrap-owner-user-id:}") String ownerUserId) {
        this.households = households;
        this.categoryService = categoryService;
        this.clock = clock;
        this.ownerUserId = ownerUserId == null ? "" : ownerUserId.trim();
    }

    public boolean canBootstrap(UUID userId) {
        return isOwner(userId)
                && households.findHouseholdIdOfUser(userId).isEmpty()
                && !households.anyHouseholdExists();
    }

    @Transactional
    public Household bootstrap(AuthenticatedPrincipal principal) {
        UUID userId = principal.userId();
        if (households.findHouseholdIdOfUser(userId).isPresent()) {
            throw new ConflictException("You already belong to a household.");
        }
        if (!isOwner(userId)) {
            throw new ForbiddenException("You are not allowed to create the first household. Ask for an invite.");
        }
        if (households.anyHouseholdExists()) {
            throw new ConflictException("A household already exists. Ask for an invite.");
        }
        Household household = households.create(new Household(UUID.randomUUID(), "Our household"));
        households.addMember(household.id(), userId, clock.instant());
        categoryService.seedDefaultsIfNone(household.id());
        return household;
    }

    private boolean isOwner(UUID userId) {
        return !ownerUserId.isEmpty() && ownerUserId.equalsIgnoreCase(userId.toString());
    }
}
