package com.budgettracker.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HouseholdJpaRepository extends JpaRepository<HouseholdEntity, UUID> {

    @Query("SELECT h.id FROM HouseholdEntity h")
    List<UUID> findAllIds();
}
