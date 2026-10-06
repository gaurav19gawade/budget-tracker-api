package com.budgettracker;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgettracker.application.CategoryService;
import com.budgettracker.application.port.BankDataProvider;
import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.domain.Household;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class BudgetFlowTests extends IntegrationTestBase {

    static final UUID MEMBER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final UUID OTHER  = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    static final String SETUP_TOKEN = "dGVzdC1idWRnZXQ=";
    static final String ACCESS_URL  = "https://user:pass@beta-bridge.simplefin.org/simplefin";
    static final String ACC_ID      = "acc_budget_001";

    // Use a fixed month well in the past so postedDate is always in-range for the test
    static final YearMonth TEST_MONTH = YearMonth.of(2026, 10);
    static final String MONTH_PARAM   = "2026-10";
    static final LocalDate TX_DATE    = LocalDate.of(2026, 10, 1);

    @Autowired MockMvc mvc;
    @Autowired HouseholdRepository households;
    @Autowired CategoryService categoryService;
    @Autowired ObjectMapper mapper;

    UUID householdId;
    String catFood;
    String catIncome;

    @BeforeEach
    void setup() throws Exception {
        jdbc.update("INSERT INTO budget.app_user (id, email) VALUES (?, ?) ON CONFLICT DO NOTHING",
                MEMBER, MEMBER + "@example.com");
        jdbc.update("INSERT INTO budget.app_user (id, email) VALUES (?, ?) ON CONFLICT DO NOTHING",
                OTHER, OTHER + "@example.com");

        Household h1 = households.create(new Household(UUID.randomUUID(), "H1"));
        households.addMember(h1.id(), MEMBER, Instant.now());
        categoryService.seedDefaultsIfNone(h1.id());
        householdId = h1.id();

        Household h2 = households.create(new Household(UUID.randomUUID(), "H2"));
        households.addMember(h2.id(), OTHER, Instant.now());
        categoryService.seedDefaultsIfNone(h2.id());

        // Create test categories
        catFood   = createCategory(MEMBER, "Restaurants", "#F97316", "🍔");
        catIncome = createCategory(MEMBER, "My Income",  "#10B981", "💰");

        // SimpleFin mocks
        when(bankData.claim(eq(SETUP_TOKEN))).thenReturn(ACCESS_URL);
        when(bankData.fetchAccounts(eq(ACCESS_URL))).thenReturn(List.of(
                new BankDataProvider.ProviderAccount(ACC_ID, ACC_ID,
                        "Chase", "Checking", "depository", "checking", "9999", "USD", "open",
                        new BigDecimal("5000"), new BigDecimal("5000"))));
        when(bankData.fetchTransactionsWithBalances(eq(ACCESS_URL), any())).thenReturn(
                new BankDataProvider.SyncResult(
                        List.of(new BankDataProvider.ProviderAccount(ACC_ID, ACC_ID,
                                "Chase", "Checking", "depository", "checking", "9999", "USD", "open",
                                new BigDecimal("4900"), new BigDecimal("4900"))),
                        List.of(
                                new BankDataProvider.ProviderTransaction(
                                        "tx-lunch", ACC_ID, new BigDecimal("-25.00"), "USD",
                                        "CHIPOTLE", "Chipotle", null, TX_DATE, null, false),
                                new BankDataProvider.ProviderTransaction(
                                        "tx-dinner", ACC_ID, new BigDecimal("-40.00"), "USD",
                                        "RESTAURANT", "Restaurant", null, TX_DATE, null, false),
                                new BankDataProvider.ProviderTransaction(
                                        "tx-paycheck", ACC_ID, new BigDecimal("3000.00"), "USD",
                                        "PAYROLL", "Employer", null, TX_DATE, null, false))));
    }

    static RequestPostProcessor as(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@example.com"));
    }

    // ---- set / get / delete budget ----------------------------------------------

    @Test
    void setBudgetAndRetrieveInSummary() throws Exception {
        // Set $200 budget for Food category
        mvc.perform(put("/api/budgets/" + catFood + "?month=" + MONTH_PARAM).with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(200));

        mvc.perform(get("/api/budgets?month=" + MONTH_PARAM).with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId=='" + catFood + "')].budgeted").value(hasItem(200.0)));
    }

    @Test
    void deleteBudget() throws Exception {
        mvc.perform(put("/api/budgets/" + catFood + "?month=" + MONTH_PARAM).with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100}"))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/budgets/" + catFood + "?month=" + MONTH_PARAM).with(as(MEMBER)))
                .andExpect(status().isNoContent());

        // After deleting the budget and with no transactions, the category is absent from the summary
        mvc.perform(get("/api/budgets?month=" + MONTH_PARAM).with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId=='" + catFood + "')]").isEmpty());
    }

    @Test
    void upsertBudgetUpdatesExisting() throws Exception {
        mvc.perform(put("/api/budgets/" + catFood + "?month=" + MONTH_PARAM).with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/budgets/" + catFood + "?month=" + MONTH_PARAM).with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250));
    }

    // ---- summary matches transaction sums ---------------------------------------

    @Test
    void summarySpentMatchesTransactionSums() throws Exception {
        connectAndSync(MEMBER);

        // Manually assign both expense transactions to catFood
        String txLunchId  = getTransactionId(MEMBER, "Chipotle");
        String txDinnerId = getTransactionId(MEMBER, "Restaurant");
        setCategory(MEMBER, txLunchId, catFood);
        setCategory(MEMBER, txDinnerId, catFood);

        mvc.perform(put("/api/budgets/" + catFood + "?month=" + MONTH_PARAM).with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isOk());

        // spent = 25 + 40 = 65; available = 100 - 65 = 35
        mvc.perform(get("/api/budgets?month=" + MONTH_PARAM).with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId=='" + catFood + "')].spent").value(hasItem(65.0)))
                .andExpect(jsonPath("$[?(@.categoryId=='" + catFood + "')].available").value(hasItem(35.0)));
    }

    @Test
    void summaryIncludesIncome() throws Exception {
        connectAndSync(MEMBER);
        String txPayId = getTransactionId(MEMBER, "Employer");
        setCategory(MEMBER, txPayId, catIncome);

        mvc.perform(get("/api/budgets?month=" + MONTH_PARAM).with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId=='" + catIncome + "')].income").value(hasItem(3000.0)));
    }

    @Test
    void summaryTotalsRowIsLast() throws Exception {
        mvc.perform(put("/api/budgets/" + catFood + "?month=" + MONTH_PARAM).with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":150}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/budgets?month=" + MONTH_PARAM).with(as(MEMBER)))
                .andExpect(status().isOk())
                // Last element should be the totals row (categoryId == null)
                .andExpect(jsonPath("$[-1:].categoryId").value(hasItem((Object) null)));
    }

    @Test
    void overBudgetAvailableIsNegative() throws Exception {
        connectAndSync(MEMBER);
        String txLunchId  = getTransactionId(MEMBER, "Chipotle");
        String txDinnerId = getTransactionId(MEMBER, "Restaurant");
        setCategory(MEMBER, txLunchId, catFood);
        setCategory(MEMBER, txDinnerId, catFood);

        // Budget only $50, but spent $65 — available should be -15
        mvc.perform(put("/api/budgets/" + catFood + "?month=" + MONTH_PARAM).with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/budgets?month=" + MONTH_PARAM).with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId=='" + catFood + "')].available").value(hasItem(-15.0)));
    }

    // ---- copy previous month ----------------------------------------------------

    @Test
    void copyPreviousMonth() throws Exception {
        String prevMonth = "2026-09";

        mvc.perform(put("/api/budgets/" + catFood + "?month=" + prevMonth).with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":300}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/budgets/copy-previous?month=" + MONTH_PARAM).with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copied").value(1));

        mvc.perform(get("/api/budgets?month=" + MONTH_PARAM).with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId=='" + catFood + "')].budgeted").value(hasItem(300.0)));
    }

    // ---- household isolation ----------------------------------------------------

    @Test
    void budgetsAreHouseholdIsolated() throws Exception {
        mvc.perform(put("/api/budgets/" + catFood + "?month=" + MONTH_PARAM).with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":200}"))
                .andExpect(status().isOk());

        // OTHER has their own categories — MEMBER's catFood should not appear for OTHER
        mvc.perform(get("/api/budgets?month=" + MONTH_PARAM).with(as(OTHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId=='" + catFood + "')]").isEmpty());
    }

    // ---- helpers ----------------------------------------------------------------

    private void connectAndSync(UUID userId) throws Exception {
        mvc.perform(post("/api/simplefin/connections").with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setupToken\":\"" + SETUP_TOKEN + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/sync").with(as(userId))).andExpect(status().isOk());
    }

    private String createCategory(UUID userId, String name, String color, String icon) throws Exception {
        MvcResult r = mvc.perform(post("/api/categories").with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"color\":\"" + color + "\",\"icon\":\"" + icon + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String getTransactionId(UUID userId, String payee) throws Exception {
        MvcResult r = mvc.perform(get("/api/transactions").with(as(userId)))
                .andExpect(status().isOk()).andReturn();
        JsonNode txs = mapper.readTree(r.getResponse().getContentAsString());
        for (JsonNode tx : txs) {
            if (payee.equals(tx.path("payee").asText())) return tx.get("id").asText();
        }
        throw new IllegalStateException("Transaction with payee '" + payee + "' not found");
    }

    private void setCategory(UUID userId, String txId, String categoryId) throws Exception {
        mvc.perform(patch("/api/transactions/" + txId + "/category").with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\"}"))
                .andExpect(status().isNoContent());
    }
}
