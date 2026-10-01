package com.budgettracker.application.identity;

import java.util.UUID;

/** Who is calling, as established by the authentication layer. */
public record AuthenticatedPrincipal(UUID userId, String email, String displayName) {
}
