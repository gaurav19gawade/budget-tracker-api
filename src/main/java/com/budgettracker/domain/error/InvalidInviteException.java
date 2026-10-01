package com.budgettracker.domain.error;

/** The invite does not exist, is expired, revoked or already used. Deliberately one generic message. */
public class InvalidInviteException extends RuntimeException {
    public InvalidInviteException() {
        super("This invite is invalid or has expired.");
    }
}
