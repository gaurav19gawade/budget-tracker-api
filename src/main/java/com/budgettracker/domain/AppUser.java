package com.budgettracker.domain;

import java.util.UUID;

public record AppUser(UUID id, String email, String displayName) {

    public AppUser withEmail(String newEmail) {
        return new AppUser(id, newEmail, displayName);
    }
}
