package com.budgettracker.domain.error;

/** The signed-in user is not a member of any household yet. */
public class NoHouseholdException extends RuntimeException {
    public NoHouseholdException() {
        super("You are not part of a household yet. Ask for an invite.");
    }
}
