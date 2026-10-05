package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.CategoryRepository;
import com.budgettracker.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;

    CategoryRepositoryAdapter(CategoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Category save(Category c) {
        CategoryEntity e = (c.id() != null)
                ? jpa.findById(c.id()).orElse(new CategoryEntity())
                : new CategoryEntity();
        e.id = c.id() != null ? c.id() : UUID.randomUUID();
        e.householdId = c.householdId();
        e.name = c.name();
        e.color = c.color();
        e.icon = c.icon();
        e.isSystem = c.isSystem();
        e.createdAt = c.createdAt();
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public List<Category> findByHouseholdId(UUID householdId) {
        return jpa.findByHouseholdIdOrderByCreatedAtAsc(householdId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existsByHouseholdId(UUID householdId) {
        return jpa.existsByHouseholdId(householdId);
    }

    private Category toDomain(CategoryEntity e) {
        return new Category(e.id, e.householdId, e.name, e.color, e.icon, e.isSystem, e.createdAt);
    }
}
