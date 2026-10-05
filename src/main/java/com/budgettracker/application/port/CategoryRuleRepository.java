package com.budgettracker.application.port;

import com.budgettracker.domain.CategoryRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRuleRepository {
    CategoryRule save(CategoryRule rule);
    /** Returns all rules for the household sorted by priority ascending (lowest number = first). */
    List<CategoryRule> findByHouseholdId(UUID householdId);
    Optional<CategoryRule> findById(UUID id);
    void deleteById(UUID id);
}
