package com.budgettracker.application.port;

import com.budgettracker.domain.Budget;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository {

    Budget save(Budget budget);

    List<Budget> findByHouseholdIdAndMonth(UUID householdId, LocalDate month);

    Optional<Budget> findByHouseholdIdAndCategoryIdAndMonth(UUID householdId, UUID categoryId, LocalDate month);

    void deleteById(UUID id);

    /** Returns all budgets for a household in a given month range (inclusive), used for copy-previous. */
    List<Budget> findByHouseholdIdAndMonthBetween(UUID householdId, LocalDate from, LocalDate to);
}
