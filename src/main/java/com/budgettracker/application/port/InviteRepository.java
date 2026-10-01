package com.budgettracker.application.port;

import com.budgettracker.domain.HouseholdInvite;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository {

    HouseholdInvite save(HouseholdInvite invite);

    Optional<HouseholdInvite> findByTokenHash(String tokenHash);

    /** Scoped lookup: an invite of another household is simply "not found". */
    Optional<HouseholdInvite> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<HouseholdInvite> findByHousehold(UUID householdId);

    /**
     * Atomically marks the invite as used if it is still redeemable.
     *
     * @return true if this call consumed the invite (false if someone else did, or it expired/was revoked)
     */
    boolean markUsed(UUID inviteId, UUID userId, Instant now);
}
