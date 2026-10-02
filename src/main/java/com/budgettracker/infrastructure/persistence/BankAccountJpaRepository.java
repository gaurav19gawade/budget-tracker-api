package com.budgettracker.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface BankAccountJpaRepository extends JpaRepository<BankAccountEntity, UUID> {

    List<BankAccountEntity> findByHouseholdIdAndStatus(UUID householdId, String status);

    List<BankAccountEntity> findByEnrollmentId(UUID enrollmentId);

    @Modifying
    @Transactional
    @Query("UPDATE BankAccountEntity a SET a.status = 'removed', a.removedAt = :removedAt WHERE a.id = :id")
    void markRemoved(UUID id, java.time.Instant removedAt);
}
