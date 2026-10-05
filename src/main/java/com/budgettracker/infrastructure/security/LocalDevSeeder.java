package com.budgettracker.infrastructure.security;

import com.budgettracker.application.CategoryService;
import com.budgettracker.application.UserProvisioner;
import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.domain.Household;
import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * LOCAL ONLY: creates the fixed dev user and a household so household scoping is exercised
 * locally exactly as in production, even though there is no login.
 */
@Component
@Profile("local")
class LocalDevSeeder implements ApplicationRunner {

    private final UserProvisioner provisioner;
    private final HouseholdRepository households;
    private final CategoryService categoryService;
    private final Clock clock;

    LocalDevSeeder(UserProvisioner provisioner, HouseholdRepository households,
                   CategoryService categoryService, Clock clock) {
        this.provisioner = provisioner;
        this.households = households;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        provisioner.ensure(new AuthenticatedPrincipal(
                LocalCurrentUserProvider.DEV_USER_ID, LocalCurrentUserProvider.DEV_EMAIL, "Local Dev"));
        if (households.findById(LocalCurrentUserProvider.DEV_HOUSEHOLD_ID).isEmpty()) {
            households.create(new Household(LocalCurrentUserProvider.DEV_HOUSEHOLD_ID, "Local household"));
        }
        if (households.findHouseholdIdOfUser(LocalCurrentUserProvider.DEV_USER_ID).isEmpty()) {
            households.addMember(LocalCurrentUserProvider.DEV_HOUSEHOLD_ID,
                    LocalCurrentUserProvider.DEV_USER_ID, clock.instant());
        }
        categoryService.seedDefaultsIfNone(LocalCurrentUserProvider.DEV_HOUSEHOLD_ID);
    }
}
