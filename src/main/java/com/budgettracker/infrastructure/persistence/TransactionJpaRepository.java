package com.budgettracker.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByHouseholdIdAndProviderId(UUID householdId, String providerId);

    List<TransactionEntity> findByHouseholdIdAndPostedDateBetweenOrderByPostedDateDescCreatedAtDesc(
            UUID householdId, LocalDate from, LocalDate to);
}
