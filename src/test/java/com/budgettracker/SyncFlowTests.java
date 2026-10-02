package com.budgettracker;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgettracker.application.port.BankDataProvider;
import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.domain.Household;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class SyncFlowTests extends IntegrationTestBase {

    static final UUID MEMBER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID OTHER_USER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    static final String SETUP_TOKEN = "dGVzdC1zZXR1cC10b2tlbg==";
    static final String ACCESS_URL = "https://user:pass@beta-bridge.simplefin.org/simplefin";
    static final String ACCOUNT_ID_1 = "acc_sync_001";

    @Autowired MockMvc mvc;
    @Autowired HouseholdRepository households;

    @BeforeEach
    void setUpHouseholds() {
        jdbc.update("INSERT INTO budget.app_user (id, email) VALUES (?, ?) ON CONFLICT DO NOTHING",
                MEMBER, MEMBER + "@example.com");
        jdbc.update("INSERT INTO budget.app_user (id, email) VALUES (?, ?) ON CONFLICT DO NOTHING",
                OTHER_USER, OTHER_USER + "@example.com");

        Household h1 = households.create(new Household(UUID.randomUUID(), "Test Household"));
        households.addMember(h1.id(), MEMBER, Instant.now());

        Household h2 = households.create(new Household(UUID.randomUUID(), "Other Household"));
        households.addMember(h2.id(), OTHER_USER, Instant.now());

        // Default mock: claim returns ACCESS_URL, fetchAccounts returns one account.
        when(bankData.claim(eq(SETUP_TOKEN))).thenReturn(ACCESS_URL);
        when(bankData.fetchAccounts(eq(ACCESS_URL))).thenReturn(List.of(
                new BankDataProvider.ProviderAccount(ACCOUNT_ID_1, ACCOUNT_ID_1,
                        "Chase", "My Checking", "depository", "checking", "1234", "USD", "open",
                        new BigDecimal("1000.00"), new BigDecimal("1100.00"))));

        // Default sync mock: returns updated account + two transactions.
        when(bankData.fetchTransactionsWithBalances(eq(ACCESS_URL), any())).thenReturn(
                new BankDataProvider.SyncResult(
                        List.of(new BankDataProvider.ProviderAccount(ACCOUNT_ID_1, ACCOUNT_ID_1,
                                "Chase", "My Checking", "depository", "checking", "1234", "USD", "open",
                                new BigDecimal("950.00"), new BigDecimal("1050.00"))),
                        List.of(
                                new BankDataProvider.ProviderTransaction(
                                        "tx-001", ACCOUNT_ID_1,
                                        new BigDecimal("-45.67"), "USD",
                                        "STARBUCKS #123", "Starbucks", null,
                                        LocalDate.now().minusDays(2), null, false),
                                new BankDataProvider.ProviderTransaction(
                                        "tx-002", ACCOUNT_ID_1,
                                        new BigDecimal("-120.00"), "USD",
                                        "WHOLE FOODS", "Whole Foods", null,
                                        LocalDate.now().minusDays(1), null, false))));
    }

    static RequestPostProcessor asUser(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@example.com"));
    }

    // ---- authentication / authorisation -----------------------------------------

    @Test
    void syncRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/sync")).andExpect(status().isUnauthorized());
    }

    @Test
    void syncRequiresHousehold() throws Exception {
        UUID noHousehold = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        mvc.perform(post("/api/sync").with(asUser(noHousehold)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_HOUSEHOLD"));
    }

    @Test
    void listTransactionsRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    // ---- sync -------------------------------------------------------------------

    @Test
    void syncReturnsZeroWhenNoAccounts() throws Exception {
        mvc.perform(post("/api/sync").with(asUser(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newTransactions").value(0))
                .andExpect(jsonPath("$.updatedTransactions").value(0));
    }

    @Test
    void syncFetchesAndStoresTransactions() throws Exception {
        connectAccount(MEMBER);

        mvc.perform(post("/api/sync").with(asUser(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newTransactions").value(2))
                .andExpect(jsonPath("$.updatedTransactions").value(0));
    }

    @Test
    void syncIsIdempotent() throws Exception {
        connectAccount(MEMBER);

        mvc.perform(post("/api/sync").with(asUser(MEMBER))).andExpect(status().isOk());

        // Second sync: same transactions — all should be updates, not inserts.
        mvc.perform(post("/api/sync").with(asUser(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newTransactions").value(0))
                .andExpect(jsonPath("$.updatedTransactions").value(2));
    }

    // ---- list transactions ------------------------------------------------------

    @Test
    void listReturnsTransactionsAfterSync() throws Exception {
        connectAccount(MEMBER);
        mvc.perform(post("/api/sync").with(asUser(MEMBER))).andExpect(status().isOk());

        mvc.perform(get("/api/transactions").with(asUser(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].description").value("WHOLE FOODS"));
    }

    @Test
    void listIsEmptyBeforeSync() throws Exception {
        mvc.perform(get("/api/transactions").with(asUser(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void transactionsAreHouseholdIsolated() throws Exception {
        connectAccount(MEMBER);
        mvc.perform(post("/api/sync").with(asUser(MEMBER))).andExpect(status().isOk());

        // OTHER_USER has no accounts and should see no transactions.
        mvc.perform(get("/api/transactions").with(asUser(OTHER_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- helpers ----------------------------------------------------------------

    private void connectAccount(UUID userId) throws Exception {
        mvc.perform(post("/api/simplefin/connections").with(asUser(userId))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"setupToken":"%s"}
                                """.formatted(SETUP_TOKEN)))
                .andExpect(status().isCreated());
    }
}
