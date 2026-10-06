package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.TransactionRepository;
import com.budgettracker.domain.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
// Object[] used for aggregate result rows
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
            // category_id and category_override are intentionally NOT updated here:
            // sync never overwrites a user's manual categorisation choice.
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
        e.categoryId = t.categoryId();
        e.categoryOverride = t.categoryOverride();
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

    @Override
    public List<Transaction> findAllByHouseholdId(UUID householdId) {
        return jpa.findByHouseholdIdOrderByPostedDateDescCreatedAtDesc(householdId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void updateCategory(UUID transactionId, UUID categoryId, boolean categoryOverride) {
        jpa.updateCategory(transactionId, categoryId, categoryOverride);
    }

    @Override
    @Transactional
    public void reassignCategory(UUID fromCategoryId, UUID toCategoryId) {
        jpa.reassignCategory(fromCategoryId, toCategoryId);
    }

    @Override
    public List<Object[]> sumByCategoryForPeriod(UUID householdId, LocalDate from, LocalDate to) {
        return jpa.sumByCategoryForPeriod(householdId, from, to);
    }

    @Override
    public List<Transaction> findPostedNonTransferByHouseholdId(UUID householdId) {
        return jpa.findByHouseholdIdAndPendingFalseAndIsInternalTransferFalse(householdId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markAsTransferPair(UUID id1, UUID id2, UUID transferGroupId) {
        jpa.markAsTransferPair(id1, id2, transferGroupId);
    }

    private Transaction toDomain(TransactionEntity e) {
        return new Transaction(
                e.id, e.householdId, e.accountId, e.providerId,
                e.amount, e.currency, e.description, e.payee, e.memo,
                e.postedDate, e.transactedAt, e.pending,
                e.isInternalTransfer, e.transferGroupId,
                e.categoryId, e.categoryOverride,
                e.createdAt, e.updatedAt);
    }
}
