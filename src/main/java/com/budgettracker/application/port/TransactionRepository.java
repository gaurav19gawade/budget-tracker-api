package com.budgettracker.application.port;

import com.budgettracker.domain.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
// Object[] used for aggregate result rows

public interface TransactionRepository {

    /**
     * Creates or updates a transaction identified by (householdId, providerId).
     * On update, category fields are preserved (manual overrides are never overwritten by sync).
     *
     * @return true if the row was newly inserted, false if an existing row was updated
     */
    boolean upsert(Transaction transaction);

    /** Date-windowed query used by the transactions list endpoint. */
    List<Transaction> findByHouseholdId(UUID householdId, LocalDate from, LocalDate to);

    /** Full scan with no date filter — used when re-applying categorisation rules. */
    List<Transaction> findAllByHouseholdId(UUID householdId);

    /** Sets the category on a single transaction. Pass categoryOverride=true for manual picks. */
    void updateCategory(UUID transactionId, UUID categoryId, boolean categoryOverride);

    /**
     * Bulk-reassigns transactions from one category to another (pass null to un-categorise).
     * Used when a category is deleted.
     */
    void reassignCategory(UUID fromCategoryId, UUID toCategoryId);

    /**
     * Returns per-category spend aggregates for the given household and date window.
     * Each entry is {categoryId (nullable UUID), sumAmount (BigDecimal)}.
     * Only posted, non-transfer transactions are included.
     */
    List<Object[]> sumByCategoryForPeriod(UUID householdId, LocalDate from, LocalDate to);
}
