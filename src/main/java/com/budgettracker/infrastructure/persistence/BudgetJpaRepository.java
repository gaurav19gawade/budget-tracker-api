package com.budgettracker.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BudgetJpaRepository extends JpaRepository<BudgetEntity, UUID> {

    List<BudgetEntity> findByHouseholdIdAndMonth(UUID householdId, LocalDate month);

    @Query("SELECT b FROM BudgetEntity b WHERE b.householdId = :hid " +
           "AND ((b.categoryId = :cid) OR (:cid IS NULL AND b.categoryId IS NULL)) " +
           "AND b.month = :month")
    Optional<BudgetEntity> findByHouseholdIdAndCategoryIdAndMonth(
            @Param("hid") UUID householdId,
            @Param("cid") UUID categoryId,
            @Param("month") LocalDate month);

    List<BudgetEntity> findByHouseholdIdAndMonthBetween(UUID householdId, LocalDate from, LocalDate to);
}
