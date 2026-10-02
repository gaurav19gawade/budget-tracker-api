package com.budgettracker.infrastructure.simplefin;

import com.budgettracker.application.port.BankDataProvider;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around the SimpleFin Bridge REST API.
 *
 * <p>Flow:
 * <ol>
 *   <li>User visits bridge.simplefin.org/create, connects their bank, copies a setup token.
 *   <li>{@link #claimAccessUrl} decodes the base64 token → claim URL, then POSTs to it to
 *       exchange it for a persistent access URL (one-time operation).
 *   <li>{@link #fetchAccounts} GETs {accessUrl}/accounts using credentials embedded in the URL.
 *   <li>{@link #fetchTransactionsWithBalances} GETs {accessUrl}/accounts?start-date={epoch}
 *       to get both updated balances and transactions since the given date.
 * </ol>
 */
public class SimpleFinClient {

    private final RestClient http;

    public SimpleFinClient(RestClient http) {
        this.http = http;
    }

    /** Decodes the setup token and POSTs to the claim URL; returns the access URL. */
    public String claimAccessUrl(String setupToken) {
        byte[] decoded = Base64.getMimeDecoder().decode(setupToken.strip());
        String claimUrl = new String(decoded, StandardCharsets.UTF_8).strip();
        String accessUrl = http.post()
                .uri(URI.create(claimUrl))
                .retrieve()
                .body(String.class);
        if (accessUrl == null || accessUrl.isBlank()) {
            throw new IllegalStateException("SimpleFin returned an empty access URL.");
        }
        return accessUrl.strip();
    }

    /** GETs {accessUrl}/accounts; credentials are extracted from the URL and sent as Basic auth. */
    public List<BankDataProvider.ProviderAccount> fetchAccounts(String accessUrl) {
        SimpleFinAccountsResponse response = doGet(accessUrl, null);
        return parseAccounts(response);
    }

    /**
     * GETs {accessUrl}/accounts?start-date={since epoch seconds}.
     * Returns updated balances and all transactions since {@code since}.
     */
    public BankDataProvider.SyncResult fetchTransactionsWithBalances(String accessUrl, Instant since) {
        SimpleFinAccountsResponse response = doGet(accessUrl, since);

        List<BankDataProvider.ProviderAccount> accounts = parseAccounts(response);
        List<BankDataProvider.ProviderTransaction> transactions = parseTransactions(response);
        return new BankDataProvider.SyncResult(accounts, transactions);
    }

    // ---- private helpers -------------------------------------------------------

    private SimpleFinAccountsResponse doGet(String accessUrl, Instant startDate) {
        URI uri = URI.create(accessUrl.trim() + "/accounts");
        String userInfo = uri.getUserInfo();
        if (userInfo == null || userInfo.isBlank()) {
            throw new IllegalArgumentException("SimpleFin access URL has no embedded credentials.");
        }
        // Rebuild the URL without embedded credentials.
        String base = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                + uri.getPath();
        String requestUrl = startDate != null
                ? base + "?start-date=" + startDate.getEpochSecond()
                : base;

        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString(userInfo.getBytes(StandardCharsets.UTF_8));

        SimpleFinAccountsResponse response = http.get()
                .uri(requestUrl)
                .header("Authorization", authHeader)
                .retrieve()
                .body(SimpleFinAccountsResponse.class);
        return response != null ? response : new SimpleFinAccountsResponse(null, null);
    }

    private List<BankDataProvider.ProviderAccount> parseAccounts(SimpleFinAccountsResponse response) {
        if (response.accounts() == null) return List.of();
        return response.accounts().stream()
                .map(a -> new BankDataProvider.ProviderAccount(
                        a.id(),
                        a.id(),
                        a.org() != null ? a.org().name() : "Unknown",
                        a.name(),
                        "depository",
                        null,
                        null,
                        a.currency() != null ? a.currency() : "USD",
                        "open",
                        parseDecimal(a.availableBalance()),
                        parseDecimal(a.balance())))
                .toList();
    }

    private List<BankDataProvider.ProviderTransaction> parseTransactions(
            SimpleFinAccountsResponse response) {
        if (response.accounts() == null) return List.of();
        List<BankDataProvider.ProviderTransaction> result = new ArrayList<>();
        for (SimpleFinAccountsResponse.SimpleFinAccount account : response.accounts()) {
            List<SimpleFinAccountsResponse.SimpleFinTransaction> txns = account.transactions();
            if (txns == null) continue;
            for (SimpleFinAccountsResponse.SimpleFinTransaction t : txns) {
                boolean pending = (t.posted() == 0);
                LocalDate postedDate = pending ? null
                        : Instant.ofEpochSecond(t.posted()).atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate transactedAt = (t.transactedAt() > 0)
                        ? Instant.ofEpochSecond(t.transactedAt()).atZone(ZoneOffset.UTC).toLocalDate()
                        : null;
                result.add(new BankDataProvider.ProviderTransaction(
                        t.id(),
                        account.id(),
                        parseDecimal(t.amount()),
                        account.currency() != null ? account.currency() : "USD",
                        t.description(),
                        t.payee(),
                        t.memo(),
                        postedDate,
                        transactedAt,
                        pending));
            }
        }
        return Collections.unmodifiableList(result);
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
