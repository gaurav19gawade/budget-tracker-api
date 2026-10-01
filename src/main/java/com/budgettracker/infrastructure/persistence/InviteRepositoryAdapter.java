package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.InviteRepository;
import com.budgettracker.domain.HouseholdInvite;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class InviteRepositoryAdapter implements InviteRepository {

    private final HouseholdInviteJpaRepository jpa;

    InviteRepositoryAdapter(HouseholdInviteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public HouseholdInvite save(HouseholdInvite invite) {
        return toDomain(jpa.saveAndFlush(toEntity(invite)));
    }

    @Override
    public Optional<HouseholdInvite> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(InviteRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<HouseholdInvite> findByIdAndHouseholdId(UUID id, UUID householdId) {
        return jpa.findByIdAndHouseholdId(id, householdId).map(InviteRepositoryAdapter::toDomain);
    }

    @Override
    public List<HouseholdInvite> findByHousehold(UUID householdId) {
        return jpa.findByHouseholdIdOrderByCreatedAtDesc(householdId).stream()
                .map(InviteRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean markUsed(UUID inviteId, UUID userId, Instant now) {
        return jpa.markUsed(inviteId, userId, now) == 1;
    }

    private static HouseholdInviteEntity toEntity(HouseholdInvite i) {
        HouseholdInviteEntity e = new HouseholdInviteEntity();
        e.id = i.id();
        e.householdId = i.householdId();
        e.tokenHash = i.tokenHash();
        e.createdBy = i.createdBy();
        e.createdAt = i.createdAt();
        e.expiresAt = i.expiresAt();
        e.usedAt = i.usedAt();
        e.usedBy = i.usedBy();
        e.revokedAt = i.revokedAt();
        return e;
    }

    private static HouseholdInvite toDomain(HouseholdInviteEntity e) {
        return new HouseholdInvite(e.id, e.householdId, e.tokenHash, e.createdBy, e.createdAt,
                e.expiresAt, e.usedAt, e.usedBy, e.revokedAt);
    }
}
