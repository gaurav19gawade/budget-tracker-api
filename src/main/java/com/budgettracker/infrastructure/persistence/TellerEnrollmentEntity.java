package com.budgettracker.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teller_enrollment")
class TellerEnrollmentEntity {

    @Id
    UUID id;

    @Column(name = "household_id", nullable = false)
    UUID householdId;

    @Column(name = "teller_id", nullable = false, length = 255)
    String tellerId;

    @Column(nullable = false, length = 255)
    String institution;

    @Column(name = "encrypted_token", nullable = false, columnDefinition = "TEXT")
    String encryptedToken;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
