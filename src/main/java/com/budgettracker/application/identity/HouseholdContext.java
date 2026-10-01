package com.budgettracker.application.identity;

import java.util.UUID;

/**
 * The caller plus the household every query must be scoped to. Controllers receive this
 * instead of reading the token, so household scoping lives in exactly one place.
 */
public record HouseholdContext(UUID userId, UUID householdId) {
}
