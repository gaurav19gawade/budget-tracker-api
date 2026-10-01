package com.budgettracker.infrastructure.security;

import com.budgettracker.application.identity.AuthenticatedPrincipal;
import com.budgettracker.application.port.CurrentUserProvider;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Reads the caller from the verified Supabase JWT: "sub" is the user id, "email" the address. */
@Component
@Profile("!local")
class JwtCurrentUserProvider implements CurrentUserProvider {

    @Override
    public AuthenticatedPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("No authenticated user on this request.");
        }
        Jwt jwt = token.getToken();
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("The access token has no email claim.");
        }
        return new AuthenticatedPrincipal(UUID.fromString(jwt.getSubject()), email, null);
    }
}
