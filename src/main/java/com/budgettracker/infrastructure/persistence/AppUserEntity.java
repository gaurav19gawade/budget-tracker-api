package com.budgettracker.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Persistence model only; the business model is {@code com.budgettracker.domain.AppUser}. */
@Entity
@Table(name = "app_user")
public class AppUserEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 320)
    String email;

    @Column(name = "display_name", length = 120)
    String displayName;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
