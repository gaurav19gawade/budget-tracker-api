package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.domain.AppUser;
import com.budgettracker.domain.Household;
import com.budgettracker.domain.HouseholdMember;
import com.budgettracker.domain.error.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class HouseholdRepositoryAdapter implements HouseholdRepository {

    private final HouseholdJpaRepository households;
    private final HouseholdMemberJpaRepository members;
    private final AppUserJpaRepository users;
    private final Clock clock;

    HouseholdRepositoryAdapter(HouseholdJpaRepository households,
                               HouseholdMemberJpaRepository members,
                               AppUserJpaRepository users,
                               Clock clock) {
        this.households = households;
        this.members = members;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public Household create(Household household) {
        HouseholdEntity entity = new HouseholdEntity();
        entity.id = household.id();
        entity.name = household.name();
        entity.createdAt = clock.instant();
        households.saveAndFlush(entity);
        return household;
    }

    @Override
    public Optional<Household> findById(UUID id) {
        return households.findById(id).map(e -> new Household(e.id, e.name));
    }

    @Override
    public Optional<UUID> findHouseholdIdOfUser(UUID userId) {
        return members.findById(userId).map(m -> m.householdId);
    }

    @Override
    public void addMember(UUID householdId, UUID userId, Instant joinedAt) {
        if (members.existsById(userId)) {
            throw new ConflictException("You already belong to a household.");
        }
        HouseholdMemberEntity entity = new HouseholdMemberEntity();
        entity.userId = userId;
        entity.householdId = householdId;
        entity.joinedAt = joinedAt;
        try {
            members.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("You already belong to a household.");
        }
    }

    @Override
    public List<HouseholdMember> findMembers(UUID householdId) {
        List<HouseholdMemberEntity> rows = members.findByHouseholdIdOrderByJoinedAtAsc(householdId);
        Map<UUID, AppUserEntity> usersById = users
                .findAllById(rows.stream().map(m -> m.userId).toList())
                .stream()
                .collect(Collectors.toMap(u -> u.id, Function.identity()));
        return rows.stream()
                .filter(m -> usersById.containsKey(m.userId))
                .map(m -> new HouseholdMember(UserRepositoryAdapter.toDomain(usersById.get(m.userId)), m.joinedAt))
                .toList();
    }

    @Override
    public boolean anyHouseholdExists() {
        return households.count() > 0;
    }

    @Override
    public List<UUID> findAllIds() {
        return households.findAllIds();
    }
}
