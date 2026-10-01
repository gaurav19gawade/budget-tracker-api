package com.budgettracker.application.port;

import com.budgettracker.application.identity.AuthenticatedPrincipal;

/** Source of the current caller. Implemented by JWT in production and a fixed user locally. */
public interface CurrentUserProvider {

    /** @throws IllegalStateException if there is no authenticated caller */
    AuthenticatedPrincipal require();
}
