package com.budgettracker.application.port;

import com.budgettracker.domain.Household;
import com.budgettracker.domain.HouseholdMember;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseholdRepository {

    Household create(Household household);

    Optional<Household> findById(UUID id);

    Optional<UUID> findHouseholdIdOfUser(UUID userId);

    /** @throws com.budgettracker.domain.error.ConflictException if the user is already a member somewhere */
    void addMember(UUID householdId, UUID userId, Instant joinedAt);

    List<HouseholdMember> findMembers(UUID householdId);

    boolean anyHouseholdExists();

    List<UUID> findAllIds();
}
