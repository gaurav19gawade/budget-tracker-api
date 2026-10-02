package com.budgettracker.infrastructure.persistence;

import com.budgettracker.application.port.EnrollmentRepository;
import com.budgettracker.domain.TellerEnrollment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class EnrollmentRepositoryAdapter implements EnrollmentRepository {

    private final TellerEnrollmentJpaRepository jpa;

    EnrollmentRepositoryAdapter(TellerEnrollmentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public TellerEnrollment save(TellerEnrollment enrollment) {
        TellerEnrollmentEntity entity = jpa.findById(enrollment.id()).orElse(new TellerEnrollmentEntity());
        entity.id = enrollment.id();
        entity.householdId = enrollment.householdId();
        entity.tellerId = enrollment.tellerId();
        entity.institution = enrollment.institution();
        entity.encryptedToken = enrollment.encryptedToken();
        entity.createdAt = enrollment.createdAt();
        jpa.saveAndFlush(entity);
        return enrollment;
    }

    @Override
    public Optional<TellerEnrollment> findByTellerId(String tellerId) {
        return jpa.findByTellerId(tellerId).map(this::toDomain);
    }

    @Override
    public void delete(UUID id) {
        jpa.deleteById(id);
    }

    private TellerEnrollment toDomain(TellerEnrollmentEntity e) {
        return new TellerEnrollment(e.id, e.householdId, e.tellerId,
                e.institution, e.encryptedToken, e.createdAt);
    }
}
