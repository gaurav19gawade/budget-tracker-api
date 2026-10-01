package com.budgettracker.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HouseholdInviteJpaRepository extends JpaRepository<HouseholdInviteEntity, UUID> {

    Optional<HouseholdInviteEntity> findByTokenHash(String tokenHash);

    Optional<HouseholdInviteEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<HouseholdInviteEntity> findByHouseholdIdOrderByCreatedAtDesc(UUID householdId);

    /** Single conditional UPDATE so two simultaneous redemptions cannot both succeed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update HouseholdInviteEntity i
               set i.usedAt = :now, i.usedBy = :userId
             where i.id = :id
               and i.usedAt is null
               and i.revokedAt is null
               and i.expiresAt > :now
            """)
    int markUsed(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);
}
