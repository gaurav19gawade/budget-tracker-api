package com.budgettracker.domain.error;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
