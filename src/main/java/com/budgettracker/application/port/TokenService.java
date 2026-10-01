package com.budgettracker.application.port;

public interface TokenService {

    /** A new unguessable URL-safe token. */
    String newToken();

    /** Stable hash used for storage and lookup; the raw token is never persisted. */
    String hash(String rawToken);
}
