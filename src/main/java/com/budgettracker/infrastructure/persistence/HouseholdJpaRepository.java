package com.budgettracker.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdJpaRepository extends JpaRepository<HouseholdEntity, UUID> {
}
