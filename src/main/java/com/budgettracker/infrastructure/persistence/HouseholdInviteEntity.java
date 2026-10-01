package com.budgettracker.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "household_invite")
public class HouseholdInviteEntity {

    @Id
    UUID id;

    @Column(name = "household_id", nullable = false)
    UUID householdId;

    @Column(name = "token_hash", nullable = false, length = 64)
    String tokenHash;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "used_at")
    Instant usedAt;

    @Column(name = "used_by")
    UUID usedBy;

    @Column(name = "revoked_at")
    Instant revokedAt;
}
