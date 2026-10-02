package com.budgettracker.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TellerEnrollmentJpaRepository extends JpaRepository<TellerEnrollmentEntity, UUID> {

    Optional<TellerEnrollmentEntity> findByTellerId(String tellerId);

    List<TellerEnrollmentEntity> findByHouseholdId(UUID householdId);
}
