package com.budgettracker.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The primary key is the user id: a user belongs to at most one household. */
@Entity
@Table(name = "household_member")
public class HouseholdMemberEntity {

    @Id
    @Column(name = "user_id")
    UUID userId;

    @Column(name = "household_id", nullable = false)
    UUID householdId;

    @Column(name = "joined_at", nullable = false)
    Instant joinedAt;
}
