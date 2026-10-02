package com.budgettracker.infrastructure.teller;

import com.budgettracker.application.port.BankDataProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around the Teller REST API.
 * Authentication: HTTP Basic with the access token as the username (empty password).
 * Transport security: mTLS, configured via the injected RestClient.
 */
public class TellerClient {

    private final RestClient http;

    public TellerClient(RestClient http) {
        this.http = http;
    }

    public List<BankDataProvider.ProviderAccount> fetchAccounts(String accessToken) {
        TellerAccountResponse[] accounts = http.get()
                .uri("/accounts")
                .header("Authorization", basicAuth(accessToken))
                .retrieve()
                .body(TellerAccountResponse[].class);
        if (accounts == null) return List.of();
        return Arrays.stream(accounts)
                .map(a -> new BankDataProvider.ProviderAccount(
                        a.id(), a.enrollmentId(),
                        a.institution() != null ? a.institution().name() : "Unknown",
                        a.name(), a.type(), a.subtype(), a.lastFour(),
                        a.currency() != null ? a.currency() : "USD", a.status()))
                .toList();
    }

    public BankDataProvider.ProviderBalance fetchBalance(String accessToken, String accountId) {
        TellerBalanceResponse balance = http.get()
                .uri("/accounts/{id}/balances", accountId)
                .header("Authorization", basicAuth(accessToken))
                .retrieve()
                .body(TellerBalanceResponse.class);
        if (balance == null) return new BankDataProvider.ProviderBalance(null, null);
        return new BankDataProvider.ProviderBalance(
                parseDecimal(balance.available()),
                parseDecimal(balance.ledger()));
    }

    private static String basicAuth(String accessToken) {
        byte[] credentials = (accessToken + ":").getBytes(StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(credentials);
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
