package com.budgettracker.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    record ProviderTransaction(
            String id,
            String accountId,
            BigDecimal amount,
            String currency,
            String description,
            String payee,
            String memo,
            LocalDate postedDate,
            LocalDate transactedAt,
            boolean pending) {
    }

    record SyncResult(List<ProviderAccount> accounts, List<ProviderTransaction> transactions) {
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

    /**
     * Fetch accounts with updated balances AND transactions since {@code since}.
     * One network call per connection; callers should pass the earliest relevant date
     * to avoid re-fetching already-synced data.
     */
    SyncResult fetchTransactionsWithBalances(String accessCredential, Instant since);
}
