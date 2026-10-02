package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.BankAccountRepository;
import com.budgettracker.domain.BankAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class BankAccountRepositoryAdapter implements BankAccountRepository {

    private final BankAccountJpaRepository jpa;

    BankAccountRepositoryAdapter(BankAccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public BankAccount save(BankAccount account) {
        BankAccountEntity entity = jpa.findById(account.id()).orElse(new BankAccountEntity());
        entity.id = account.id();
        entity.householdId = account.householdId();
        entity.enrollmentId = account.enrollmentId();
        entity.tellerId = account.tellerId();
        entity.institution = account.institution();
        entity.name = account.name();
        entity.type = account.type();
        entity.subtype = account.subtype();
        entity.lastFour = account.lastFour();
        entity.currency = account.currency();
        entity.balanceAvailable = account.balanceAvailable();
        entity.balanceLedger = account.balanceLedger();
        entity.lastSyncedAt = account.lastSyncedAt();
        entity.status = account.status();
        entity.createdAt = account.createdAt();
        entity.removedAt = account.removedAt();
        jpa.saveAndFlush(entity);
        return account;
    }

    @Override
    public Optional<BankAccount> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<BankAccount> findActiveByHouseholdId(UUID householdId) {
        return jpa.findByHouseholdIdAndStatus(householdId, "active").stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<BankAccount> findByEnrollmentId(UUID enrollmentId) {
        return jpa.findByEnrollmentId(enrollmentId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public void markRemoved(UUID id, Instant removedAt) {
        jpa.markRemoved(id, removedAt);
    }

    private BankAccount toDomain(BankAccountEntity e) {
        return new BankAccount(e.id, e.householdId, e.enrollmentId, e.tellerId,
                e.institution, e.name, e.type, e.subtype, e.lastFour, e.currency,
                e.balanceAvailable, e.balanceLedger, e.lastSyncedAt,
                e.status, e.createdAt, e.removedAt);
    }
}
