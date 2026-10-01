package com.budgettracker.api;

import com.budgettracker.application.HouseholdBootstrapper;
import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.domain.Household;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/households")
public class HouseholdController {

    public record HouseholdResponse(UUID id, String name) {
    }

    private final HouseholdBootstrapper bootstrapper;

    public HouseholdController(HouseholdBootstrapper bootstrapper) {
        this.bootstrapper = bootstrapper;
    }

    /** Creates the first household. Only the configured owner may call this. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdResponse create(AuthenticatedPrincipal principal) {
        Household household = bootstrapper.bootstrap(principal);
        return new HouseholdResponse(household.id(), household.name());
    }
}
