package com.budgettracker.application.port;

import com.budgettracker.domain.BankAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository {

    BankAccount save(BankAccount account);

    Optional<BankAccount> findById(UUID id);

    List<BankAccount> findActiveByHouseholdId(UUID householdId);

    /** All accounts (including removed) that belong to the given enrollment. */
    List<BankAccount> findByEnrollmentId(UUID enrollmentId);

    void markRemoved(UUID id, Instant removedAt);
}
