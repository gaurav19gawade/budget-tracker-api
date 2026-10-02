package com.budgettracker.application.port;

import java.math.BigDecimal;
import java.util.List;

/**
 * Abstraction over the bank-data provider (SimpleFin Bridge today, swappable later).
 * Callers pass the raw access credential; they never deal with HTTP details.
 */
public interface BankDataProvider {

    record ProviderAccount(
            String id,
            String connectionId,
            String institution,
            String name,
            String type,
            String subtype,
            String lastFour,
            String currency,
            String status,
            BigDecimal balanceAvailable,
            BigDecimal balanceLedger) {
    }

    /**
     * Exchange a one-time setup token for a persistent access credential.
     * For SimpleFin Bridge this decodes the base64 token and POSTs to the claim URL.
     */
    String claim(String setupToken);

    /**
     * Fetch all accounts for the given access credential, including current balances.
     */
    List<ProviderAccount> fetchAccounts(String accessCredential);
}
