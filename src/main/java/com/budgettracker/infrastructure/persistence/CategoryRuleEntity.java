package com.budgettracker.infrastructure.persistence;

import com.budgettracker.domain.CategoryRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category_rule")
class CategoryRuleEntity {

    @Id
    UUID id;

    @Column(name = "household_id", nullable = false)
    UUID householdId;

    @Column(name = "category_id", nullable = false)
    UUID categoryId;

    @Column(nullable = false)
    int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_field", nullable = false, length = 30)
    CategoryRule.MatchField matchField;

    @Column(name = "match_value", nullable = false, length = 500)
    String matchValue;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
