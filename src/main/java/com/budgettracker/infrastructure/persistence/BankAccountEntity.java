package com.budgettracker.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_account")
class BankAccountEntity {

    @Id
    UUID id;

    @Column(name = "household_id", nullable = false)
    UUID householdId;

    @Column(name = "enrollment_id", nullable = false)
    UUID enrollmentId;

    @Column(name = "teller_id", nullable = false, length = 255)
    String tellerId;

    @Column(nullable = false, length = 255)
    String institution;

    @Column(nullable = false, length = 255)
    String name;

    @Column(nullable = false, length = 50)
    String type;

    @Column(length = 50)
    String subtype;

    @Column(name = "last_four", length = 4)
    String lastFour;

    @Column(nullable = false, length = 3)
    String currency;

    @Column(name = "balance_available", precision = 19, scale = 4)
    BigDecimal balanceAvailable;

    @Column(name = "balance_ledger", precision = 19, scale = 4)
    BigDecimal balanceLedger;

    @Column(name = "last_synced_at")
    Instant lastSyncedAt;

    @Column(nullable = false, length = 20)
    String status;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "removed_at")
    Instant removedAt;
}
