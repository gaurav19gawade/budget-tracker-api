package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.UserRepository;
import com.budgettracker.domain.AppUser;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class UserRepositoryAdapter implements UserRepository {

    private final AppUserJpaRepository jpa;
    private final Clock clock;

    UserRepositoryAdapter(AppUserJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public Optional<AppUser> findById(UUID id) {
        return jpa.findById(id).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    public AppUser save(AppUser user) {
        AppUserEntity entity = jpa.findById(user.id()).orElseGet(() -> {
            AppUserEntity created = new AppUserEntity();
            created.id = user.id();
            created.createdAt = clock.instant();
            return created;
        });
        entity.email = user.email();
        entity.displayName = user.displayName();
        try {
            return toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Two first requests from the same user raced; the other one won. Use its row.
            return jpa.findById(user.id()).map(UserRepositoryAdapter::toDomain).orElseThrow(() -> e);
        }
    }

    static AppUser toDomain(AppUserEntity e) {
        return new AppUser(e.id, e.email, e.displayName);
    }
}
