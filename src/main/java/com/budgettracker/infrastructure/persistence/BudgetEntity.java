package com.budgettracker.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "budget")
class BudgetEntity {

    @Id
    UUID id;

    @Column(name = "household_id", nullable = false)
    UUID householdId;

    @Column(name = "category_id")
    UUID categoryId;

    @Column(nullable = false)
    LocalDate month;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
