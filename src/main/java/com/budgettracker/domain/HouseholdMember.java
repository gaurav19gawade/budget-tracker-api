package com.budgettracker.domain;

import java.time.Instant;

public record HouseholdMember(AppUser user, Instant joinedAt) {
}
