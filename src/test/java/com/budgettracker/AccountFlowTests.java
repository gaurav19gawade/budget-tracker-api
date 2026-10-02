package com.budgettracker;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgettracker.application.port.BankDataProvider;
import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.domain.Household;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AccountFlowTests extends IntegrationTestBase {

    static final UUID MEMBER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID OTHER_USER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    static final String SETUP_TOKEN = "dGVzdC1zZXR1cC10b2tlbg=="; // base64("test-setup-token")
    static final String ACCESS_URL = "https://user:pass@beta-bridge.simplefin.org/simplefin";
    static final String ACCOUNT_ID_1 = "acc_test_001";
    static final String ACCOUNT_ID_2 = "acc_test_002";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired HouseholdRepository households;

    @BeforeEach
    void setUpHouseholds() {
        // Provision users in app_user first (household_member.user_id FK → app_user.id).
        jdbc.update("INSERT INTO budget.app_user (id, email) VALUES (?, ?) ON CONFLICT DO NOTHING",
                MEMBER, MEMBER + "@example.com");
        jdbc.update("INSERT INTO budget.app_user (id, email) VALUES (?, ?) ON CONFLICT DO NOTHING",
                OTHER_USER, OTHER_USER + "@example.com");

        // MEMBER has a household; OTHER_USER has a separate household.
        Household h1 = households.create(new Household(UUID.randomUUID(), "Test Household"));
        households.addMember(h1.id(), MEMBER, Instant.now());

        Household h2 = households.create(new Household(UUID.randomUUID(), "Other Household"));
        households.addMember(h2.id(), OTHER_USER, Instant.now());

        // Default: claim returns ACCESS_URL, two accounts with balances.
        when(bankData.claim(eq(SETUP_TOKEN))).thenReturn(ACCESS_URL);
        when(bankData.fetchAccounts(eq(ACCESS_URL))).thenReturn(List.of(
                new BankDataProvider.ProviderAccount(ACCOUNT_ID_1, ACCOUNT_ID_1,
                        "Chase", "My Checking", "depository", "checking", "1234", "USD", "open",
                        new BigDecimal("1000.00"), new BigDecimal("1100.00")),
                new BankDataProvider.ProviderAccount(ACCOUNT_ID_2, ACCOUNT_ID_2,
                        "Chase", "My Savings", "depository", "savings", "5678", "USD", "open",
                        new BigDecimal("500.00"), new BigDecimal("500.00"))));
    }

    static RequestPostProcessor asUser(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@example.com"));
    }

    // ---- authentication / authorisation -------------------------------------------

    @Test
    void connectRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/simplefin/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(connectBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void connectRequiresAHousehold() throws Exception {
        UUID noHousehold = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        mvc.perform(post("/api/simplefin/connections").with(asUser(noHousehold))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(connectBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_HOUSEHOLD"));
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    // ---- connect ------------------------------------------------------------------

    @Test
    void connectReturnsAccounts() throws Exception {
        mvc.perform(post("/api/simplefin/connections").with(asUser(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(connectBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].institution").value("Chase"))
                .andExpect(jsonPath("$[0].lastFour").value("1234"))
                .andExpect(jsonPath("$[0].balanceAvailable").value(1000.00));
    }

    @Test
    void connectIsIdempotent() throws Exception {
        mvc.perform(post("/api/simplefin/connections").with(asUser(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content(connectBody()))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/simplefin/connections").with(asUser(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content(connectBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ---- list ---------------------------------------------------------------------

    @Test
    void listReturnsConnectedAccounts() throws Exception {
        connect(MEMBER);

        mvc.perform(get("/api/accounts").with(asUser(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void householdsAreIsolated() throws Exception {
        connect(MEMBER);

        mvc.perform(get("/api/accounts").with(asUser(OTHER_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- remove -------------------------------------------------------------------

    @Test
    void removeAccountReturns204AndAccountIsGone() throws Exception {
        connect(MEMBER);
        String accountId = firstAccountId();

        mvc.perform(delete("/api/accounts/" + accountId).with(asUser(MEMBER)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/accounts").with(asUser(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void otherHouseholdCannotRemoveAccount() throws Exception {
        connect(MEMBER);
        String accountId = firstAccountId();

        mvc.perform(delete("/api/accounts/" + accountId).with(asUser(OTHER_USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removingAlreadyRemovedAccountIsConflict() throws Exception {
        connect(MEMBER);
        String accountId = firstAccountId();

        mvc.perform(delete("/api/accounts/" + accountId).with(asUser(MEMBER)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/accounts/" + accountId).with(asUser(MEMBER)))
                .andExpect(status().isConflict());
    }

    // ---- helpers ------------------------------------------------------------------

    private void connect(UUID userId) throws Exception {
        mvc.perform(post("/api/simplefin/connections").with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content(connectBody()))
                .andExpect(status().isCreated());
    }

    private String firstAccountId() throws Exception {
        String body = mvc.perform(get("/api/accounts").with(asUser(MEMBER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get(0).get("id").asText();
    }

    private static String connectBody() {
        return """
                {"setupToken":"%s"}
                """.formatted(SETUP_TOKEN);
    }
}
