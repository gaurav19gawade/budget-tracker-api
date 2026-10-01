package com.budgettracker.application.view;

import java.time.Instant;
import java.util.UUID;

public record CreatedInvite(UUID id, String token, Instant expiresAt) {
}
