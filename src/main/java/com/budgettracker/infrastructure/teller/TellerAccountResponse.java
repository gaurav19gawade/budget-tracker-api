package com.budgettracker.infrastructure.teller;

import com.fasterxml.jackson.annotation.JsonProperty;

record TellerAccountResponse(
        String id,
        @JsonProperty("enrollment_id") String enrollmentId,
        Institution institution,
        @JsonProperty("last_four") String lastFour,
        String name,
        String status,
        String subtype,
        String type,
        String currency) {

    record Institution(String name, String id) {
    }
}
