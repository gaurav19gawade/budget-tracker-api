package com.budgettracker.application;

import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.application.view.MeView;
import com.budgettracker.domain.AppUser;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read model for "who am I and which household am I in". */
@Service
public class MeQuery {

    private final UserProvisioner provisioner;
    private final HouseholdRepository households;
    private final HouseholdBootstrapper bootstrapper;

    public MeQuery(UserProvisioner provisioner, HouseholdRepository households,
                   HouseholdBootstrapper bootstrapper) {
        this.provisioner = provisioner;
        this.households = households;
        this.bootstrapper = bootstrapper;
    }

    public MeView get(AuthenticatedPrincipal principal) {
        AppUser user = provisioner.ensure(principal);
        MeView.HouseholdView household = households.findHouseholdIdOfUser(user.id())
                .flatMap(this::loadHousehold)
                .orElse(null);
        return new MeView(user.id(), user.email(), user.displayName(), household,
                bootstrapper.canBootstrap(user.id()));
    }

    private java.util.Optional<MeView.HouseholdView> loadHousehold(UUID householdId) {
        return households.findById(householdId).map(h -> new MeView.HouseholdView(
                h.id(),
                h.name(),
                households.findMembers(householdId).stream()
                        .map(m -> new MeView.MemberView(
                                m.user().id(), m.user().email(), m.user().displayName(), m.joinedAt()))
                        .toList()));
    }
}
