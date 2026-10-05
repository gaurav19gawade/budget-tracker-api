package com.budgettracker.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryRuleJpaRepository extends JpaRepository<CategoryRuleEntity, UUID> {
    List<CategoryRuleEntity> findByHouseholdIdOrderByPriorityAsc(UUID householdId);
}
