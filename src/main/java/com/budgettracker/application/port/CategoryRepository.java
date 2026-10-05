package com.budgettracker.application.port;

import com.budgettracker.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {
    Category save(Category category);
    List<Category> findByHouseholdId(UUID householdId);
    Optional<Category> findById(UUID id);
    void deleteById(UUID id);
    boolean existsByHouseholdId(UUID householdId);
}
