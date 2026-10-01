package com.budgettracker.infrastructure.security;

import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.port.CurrentUserProvider;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** LOCAL ONLY: everyone is the same fixed development user. */
@Component
@Profile("local")
class LocalCurrentUserProvider implements CurrentUserProvider {

    static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID DEV_HOUSEHOLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final String DEV_EMAIL = "dev@local.test";

    @Override
    public AuthenticatedPrincipal require() {
        return new AuthenticatedPrincipal(DEV_USER_ID, DEV_EMAIL, "Local Dev");
    }
}
