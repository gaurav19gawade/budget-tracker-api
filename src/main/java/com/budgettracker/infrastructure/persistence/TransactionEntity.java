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
@Table(name = "transaction")
class TransactionEntity {

    @Id
    UUID id;

    @Column(name = "household_id", nullable = false)
    UUID householdId;

    @Column(name = "account_id", nullable = false)
    UUID accountId;

    @Column(name = "provider_id", nullable = false, length = 255)
    String providerId;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal amount;

    @Column(nullable = false, length = 3)
    String currency;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(length = 500)
    String payee;

    @Column(columnDefinition = "TEXT")
    String memo;

    @Column(name = "posted_date")
    LocalDate postedDate;

    @Column(name = "transacted_at")
    LocalDate transactedAt;

    @Column(nullable = false)
    boolean pending;

    @Column(name = "is_internal_transfer", nullable = false)
    boolean isInternalTransfer;

    @Column(name = "transfer_group_id")
    UUID transferGroupId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
