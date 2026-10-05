package com.budgettracker.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category")
class CategoryEntity {

    @Id
    UUID id;

    @Column(name = "household_id", nullable = false)
    UUID householdId;

    @Column(nullable = false, length = 100)
    String name;

    @Column(length = 7)
    String color;

    @Column(length = 50)
    String icon;

    @Column(name = "is_system", nullable = false)
    boolean isSystem;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
