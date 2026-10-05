package com.budgettracker.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {
    List<CategoryEntity> findByHouseholdIdOrderByCreatedAtAsc(UUID householdId);
    boolean existsByHouseholdId(UUID householdId);
}
