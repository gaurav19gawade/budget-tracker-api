package com.budgettracker.infrastructure.simplefin;

import com.budgettracker.application.port.BankDataProvider;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
        URI uri = URI.create(accessUrl.trim() + "/accounts");
        String userInfo = uri.getUserInfo();
        if (userInfo == null || userInfo.isBlank()) {
            throw new IllegalArgumentException("SimpleFin access URL has no embedded credentials.");
        }
        // Rebuild the URL without credentials for the HTTP request.
        String requestUrl = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                + uri.getPath();

        SimpleFinAccountsResponse response = http.get()
                .uri(requestUrl)
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(userInfo.getBytes(StandardCharsets.UTF_8)))
                .retrieve()
                .body(SimpleFinAccountsResponse.class);

        if (response == null || response.accounts() == null) return List.of();
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

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
