package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.TransactionRepository;
import com.budgettracker.domain.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class TransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionJpaRepository jpa;

    TransactionRepositoryAdapter(TransactionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean upsert(Transaction t) {
        Optional<TransactionEntity> existing =
                jpa.findByHouseholdIdAndProviderId(t.householdId(), t.providerId());

        if (existing.isPresent()) {
            TransactionEntity e = existing.get();
            e.amount = t.amount();
            e.description = t.description();
            e.payee = t.payee();
            e.memo = t.memo();
            e.postedDate = t.postedDate();
            e.transactedAt = t.transactedAt();
            e.pending = t.pending();
            e.updatedAt = t.updatedAt();
            jpa.saveAndFlush(e);
            return false;
        }

        TransactionEntity e = new TransactionEntity();
        e.id = t.id() != null ? t.id() : UUID.randomUUID();
        e.householdId = t.householdId();
        e.accountId = t.accountId();
        e.providerId = t.providerId();
        e.amount = t.amount();
        e.currency = t.currency();
        e.description = t.description();
        e.payee = t.payee();
        e.memo = t.memo();
        e.postedDate = t.postedDate();
        e.transactedAt = t.transactedAt();
        e.pending = t.pending();
        e.isInternalTransfer = t.isInternalTransfer();
        e.transferGroupId = t.transferGroupId();
        e.createdAt = t.createdAt();
        e.updatedAt = t.updatedAt();
        jpa.saveAndFlush(e);
        return true;
    }

    @Override
    public List<Transaction> findByHouseholdId(UUID householdId, LocalDate from, LocalDate to) {
        return jpa.findByHouseholdIdAndPostedDateBetweenOrderByPostedDateDescCreatedAtDesc(
                        householdId, from, to)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private Transaction toDomain(TransactionEntity e) {
        return new Transaction(
                e.id, e.householdId, e.accountId, e.providerId,
                e.amount, e.currency, e.description, e.payee, e.memo,
                e.postedDate, e.transactedAt, e.pending,
                e.isInternalTransfer, e.transferGroupId,
                e.createdAt, e.updatedAt);
    }
}
