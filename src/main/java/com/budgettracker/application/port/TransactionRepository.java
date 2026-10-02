package com.budgettracker.application.port;

import com.budgettracker.domain.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository {

    /**
     * Creates or updates a transaction identified by (householdId, providerId).
     *
     * @return true if the row was newly inserted, false if an existing row was updated
     */
    boolean upsert(Transaction transaction);

    List<Transaction> findByHouseholdId(UUID householdId, LocalDate from, LocalDate to);
}
