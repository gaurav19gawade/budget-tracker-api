package com.budgettracker.application;

import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.port.UserRepository;
import com.budgettracker.domain.AppUser;
import org.springframework.stereotype.Service;

/**
 * Creates the local user row the first time a signed-in person calls the API
 * (just-in-time provisioning), and keeps the stored email in sync with the token.
 */
@Service
public class UserProvisioner {

    private final UserRepository users;

    public UserProvisioner(UserRepository users) {
        this.users = users;
    }

    public AppUser ensure(AuthenticatedPrincipal principal) {
        return users.findById(principal.userId())
                .map(existing -> existing.email().equalsIgnoreCase(principal.email())
                        ? existing
                        : users.save(existing.withEmail(principal.email())))
                .orElseGet(() -> users.save(new AppUser(
                        principal.userId(), principal.email(), principal.displayName())));
    }
}
