package com.budgettracker.application.port;

import com.budgettracker.domain.AppUser;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<AppUser> findById(UUID id);

    /** Inserts or updates. Safe under concurrent first requests from the same user. */
    AppUser save(AppUser user);
}
