package com.budgettracker.application.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeView(
        UUID userId,
        String email,
        String displayName,
        HouseholdView household,
        boolean canCreateHousehold) {

    public record HouseholdView(UUID id, String name, List<MemberView> members) {
    }

    public record MemberView(UUID userId, String email, String displayName, Instant joinedAt) {
    }
}
