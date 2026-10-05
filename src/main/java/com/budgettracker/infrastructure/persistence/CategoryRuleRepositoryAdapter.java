package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.CategoryRuleRepository;
import com.budgettracker.domain.CategoryRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class CategoryRuleRepositoryAdapter implements CategoryRuleRepository {

    private final CategoryRuleJpaRepository jpa;

    CategoryRuleRepositoryAdapter(CategoryRuleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public CategoryRule save(CategoryRule r) {
        CategoryRuleEntity e = (r.id() != null)
                ? jpa.findById(r.id()).orElse(new CategoryRuleEntity())
                : new CategoryRuleEntity();
        e.id = r.id() != null ? r.id() : UUID.randomUUID();
        e.householdId = r.householdId();
        e.categoryId = r.categoryId();
        e.priority = r.priority();
        e.matchField = r.matchField();
        e.matchValue = r.matchValue();
        e.createdAt = r.createdAt();
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public List<CategoryRule> findByHouseholdId(UUID householdId) {
        return jpa.findByHouseholdIdOrderByPriorityAsc(householdId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CategoryRule> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    private CategoryRule toDomain(CategoryRuleEntity e) {
        return new CategoryRule(
                e.id, e.householdId, e.categoryId, e.priority,
                e.matchField, e.matchValue, e.createdAt);
    }
}
