package com.budgettracker.infrastructure.simplefin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Top-level response from GET {accessUrl}/accounts. */
record SimpleFinAccountsResponse(
        List<String> errors,
        List<SimpleFinAccount> accounts) {

    record SimpleFinAccount(
            String id,
            SimpleFinOrg org,
            String name,
            String currency,
            String balance,
            @JsonProperty("available-balance") String availableBalance) {
    }

    record SimpleFinOrg(
            String name,
            String domain) {
    }
}
