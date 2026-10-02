package com.budgettracker.application.port;

import java.math.BigDecimal;
import java.util.List;

/**
 * Abstraction over the bank-data provider (Teller today, swappable later).
 * Callers pass the raw access token; they never deal with mTLS or HTTP details.
 */
public interface BankDataProvider {

    record ProviderAccount(
            String id,
            String enrollmentId,
            String institution,
            String name,
            String type,
            String subtype,
            String lastFour,
            String currency,
            String status) {
    }

    record ProviderBalance(
            BigDecimal available,
            BigDecimal ledger) {
    }

    List<ProviderAccount> fetchAccounts(String accessToken);

    /**
     * Returns a balance with null values if the account type does not support balance
     * queries or if the Teller call fails non-fatally (e.g. credit cards with no available).
     */
    ProviderBalance fetchBalance(String accessToken, String accountId);
}
