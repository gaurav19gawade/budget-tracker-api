package com.budgettracker.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserJpaRepository extends JpaRepository<AppUserEntity, UUID> {
}
