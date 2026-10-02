package com.budgettracker.application.port;

import com.budgettracker.domain.TellerEnrollment;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository {

    TellerEnrollment save(TellerEnrollment enrollment);

    Optional<TellerEnrollment> findByTellerId(String tellerId);

    void delete(UUID id);
}
