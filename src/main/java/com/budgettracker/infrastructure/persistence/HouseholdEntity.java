package com.budgettracker.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "household")
public class HouseholdEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 120)
    String name;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
