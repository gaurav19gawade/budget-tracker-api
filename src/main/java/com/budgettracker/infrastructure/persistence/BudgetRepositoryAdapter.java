package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.BudgetRepository;
import com.budgettracker.domain.Budget;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class BudgetRepositoryAdapter implements BudgetRepository {

    private final BudgetJpaRepository jpa;

    BudgetRepositoryAdapter(BudgetJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Budget save(Budget b) {
        BudgetEntity e = jpa.findById(b.id()).orElseGet(BudgetEntity::new);
        e.id = b.id();
        e.householdId = b.householdId();
        e.categoryId = b.categoryId();
        e.month = b.month();
        e.amount = b.amount();
        e.createdAt = b.createdAt();
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public List<Budget> findByHouseholdIdAndMonth(UUID householdId, LocalDate month) {
        return jpa.findByHouseholdIdAndMonth(householdId, month)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Budget> findByHouseholdIdAndCategoryIdAndMonth(
            UUID householdId, UUID categoryId, LocalDate month) {
        return jpa.findByHouseholdIdAndCategoryIdAndMonth(householdId, categoryId, month)
                .map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public List<Budget> findByHouseholdIdAndMonthBetween(UUID householdId, LocalDate from, LocalDate to) {
        return jpa.findByHouseholdIdAndMonthBetween(householdId, from, to)
                .stream().map(this::toDomain).toList();
    }

    private Budget toDomain(BudgetEntity e) {
        return new Budget(e.id, e.householdId, e.categoryId, e.month, e.amount, e.createdAt);
    }
}
