package com.budgettracker.infrastructure.simplefin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Top-level response from GET {accessUrl}/accounts[?start-date=...]. */
record SimpleFinAccountsResponse(
        List<String> errors,
        List<SimpleFinAccount> accounts) {

    record SimpleFinAccount(
            String id,
            SimpleFinOrg org,
            String name,
            String currency,
            String balance,
            @JsonProperty("available-balance") String availableBalance,
            List<SimpleFinTransaction> transactions) {
    }

    record SimpleFinOrg(
            String name,
            String domain) {
    }

    record SimpleFinTransaction(
            String id,
            /** Unix epoch seconds; 0 means pending. */
            long posted,
            @JsonProperty("transacted_at") long transactedAt,
            String amount,
            String description,
            String payee,
            String memo) {
    }
}
