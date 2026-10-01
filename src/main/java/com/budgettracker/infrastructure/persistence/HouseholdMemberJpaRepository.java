package com.budgettracker.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdMemberJpaRepository extends JpaRepository<HouseholdMemberEntity, UUID> {

    List<HouseholdMemberEntity> findByHouseholdIdOrderByJoinedAtAsc(UUID householdId);
}
