package com.budgettracker.infrastructure.teller;

import com.fasterxml.jackson.annotation.JsonProperty;

record TellerBalanceResponse(
        @JsonProperty("account_id") String accountId,
        String available,
        String ledger) {
}
