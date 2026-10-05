package com.budgettracker;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgettracker.application.CategoryService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CategoryFlowTests extends IntegrationTestBase {

    static final UUID MEMBER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID OTHER  = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final String SETUP_TOKEN = "dGVzdC10b2tlbg==";
    static final String ACCESS_URL  = "https://user:pass@beta-bridge.simplefin.org/simplefin";
    static final String ACC_ID      = "acc_cat_001";

    @Autowired MockMvc mvc;
    @Autowired HouseholdRepository households;
    @Autowired CategoryService categoryService;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO budget.app_user (id, email) VALUES (?, ?) ON CONFLICT DO NOTHING",
                MEMBER, MEMBER + "@example.com");
        jdbc.update("INSERT INTO budget.app_user (id, email) VALUES (?, ?) ON CONFLICT DO NOTHING",
                OTHER, OTHER + "@example.com");

        Household h1 = households.create(new Household(UUID.randomUUID(), "H1"));
        households.addMember(h1.id(), MEMBER, Instant.now());
        categoryService.seedDefaultsIfNone(h1.id());

        Household h2 = households.create(new Household(UUID.randomUUID(), "H2"));
        households.addMember(h2.id(), OTHER, Instant.now());
        categoryService.seedDefaultsIfNone(h2.id());

        // SimpleFin mocks
        when(bankData.claim(eq(SETUP_TOKEN))).thenReturn(ACCESS_URL);
        when(bankData.fetchAccounts(eq(ACCESS_URL))).thenReturn(List.of(
                new BankDataProvider.ProviderAccount(ACC_ID, ACC_ID,
                        "Chase", "Checking", "depository", "checking", "1234", "USD", "open",
                        new BigDecimal("1000"), new BigDecimal("1000"))));
        when(bankData.fetchTransactionsWithBalances(eq(ACCESS_URL), any())).thenReturn(
                new BankDataProvider.SyncResult(
                        List.of(new BankDataProvider.ProviderAccount(ACC_ID, ACC_ID,
                                "Chase", "Checking", "depository", "checking", "1234", "USD", "open",
                                new BigDecimal("950"), new BigDecimal("950"))),
                        List.of(
                                new BankDataProvider.ProviderTransaction(
                                        "tx-starbucks", ACC_ID, new BigDecimal("-5.50"), "USD",
                                        "STARBUCKS #12", "Starbucks", null,
                                        LocalDate.now().minusDays(1), null, false),
                                new BankDataProvider.ProviderTransaction(
                                        "tx-paycheck", ACC_ID, new BigDecimal("2000.00"), "USD",
                                        "PAYROLL DEPOSIT", "Employer Inc", null,
                                        LocalDate.now().minusDays(1), null, false))));
    }

    static RequestPostProcessor as(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@example.com"));
    }

    // ---- default category seeding -----------------------------------------------

    @Test
    void defaultCategoriesSeededOnHouseholdCreate() throws Exception {
        mvc.perform(get("/api/categories").with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(14)));
    }

    @Test
    void categoriesAreHouseholdIsolated() throws Exception {
        // MEMBER has 14 defaults; OTHER also has 14 (seeded for their household)
        mvc.perform(get("/api/categories").with(as(MEMBER)))
                .andExpect(jsonPath("$", hasSize(14)));
        mvc.perform(get("/api/categories").with(as(OTHER)))
                .andExpect(jsonPath("$", hasSize(14)));
    }

    // ---- category CRUD ----------------------------------------------------------

    @Test
    void createAndListCustomCategory() throws Exception {
        mvc.perform(post("/api/categories").with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pets","color":"#A78BFA","icon":"🐾"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pets"))
                .andExpect(jsonPath("$.isSystem").value(false));

        mvc.perform(get("/api/categories").with(as(MEMBER)))
                .andExpect(jsonPath("$", hasSize(15)));
    }

    @Test
    void deleteCategoryUncategorizesTransactions() throws Exception {
        // Connect + sync to get transactions
        connectAndSync(MEMBER);

        // Create a category and rule that matches "Starbucks"
        String catId = createCategory(MEMBER, "Coffee", "#7C3AED", "☕");
        createRule(MEMBER, catId, 0, "PAYEE_CONTAINS", "Starbucks");

        // Apply rules
        mvc.perform(post("/api/category-rules/apply").with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorized").value(2));

        // Delete the Coffee category (no reassignment)
        mvc.perform(delete("/api/categories/" + catId).with(as(MEMBER)))
                .andExpect(status().isNoContent());

        // Starbucks transaction should now have null categoryId
        mvc.perform(get("/api/transactions").with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.payee=='Starbucks')].categoryId", hasSize(1)))
                .andExpect(jsonPath("$[?(@.payee=='Starbucks')].categoryId").value(hasItem(nullValue())));
    }

    // ---- rules ------------------------------------------------------------------

    @Test
    void rulePrecedenceFirstMatchWins() throws Exception {
        connectAndSync(MEMBER);

        String catFood   = createCategory(MEMBER, "Coffee",    "#7C3AED", "☕");
        String catGeneral = createCategory(MEMBER, "General",  "#9CA3AF", "📦");
        // priority 0 (higher) matches Starbucks first
        createRule(MEMBER, catFood,    0, "PAYEE_CONTAINS",   "Starbucks");
        createRule(MEMBER, catGeneral, 1, "DESCRIPTION_CONTAINS", "STARBUCKS");

        mvc.perform(post("/api/category-rules/apply").with(as(MEMBER)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/transactions").with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.payee=='Starbucks')].categoryId").value(hasItem(catFood)));
    }

    @Test
    void applyRulesIsIdempotent() throws Exception {
        connectAndSync(MEMBER);
        String catId = createCategory(MEMBER, "Coffee", "#7C3AED", "☕");
        createRule(MEMBER, catId, 0, "PAYEE_CONTAINS", "Starbucks");

        mvc.perform(post("/api/category-rules/apply").with(as(MEMBER))).andExpect(status().isOk());
        mvc.perform(post("/api/category-rules/apply").with(as(MEMBER))).andExpect(status().isOk());

        // Still the same category after two runs
        mvc.perform(get("/api/transactions").with(as(MEMBER)))
                .andExpect(jsonPath("$[?(@.payee=='Starbucks')].categoryId").value(hasItem(catId)));
    }

    @Test
    void manualOverrideWinsOverApplyRules() throws Exception {
        connectAndSync(MEMBER);

        // Create a rule that would assign Coffee to Starbucks
        String catCoffee = createCategory(MEMBER, "Coffee",    "#7C3AED", "☕");
        String catManual = createCategory(MEMBER, "Manual",    "#EF4444", "✋");
        createRule(MEMBER, catCoffee, 0, "PAYEE_CONTAINS", "Starbucks");

        // Manually set the Starbucks transaction to catManual
        String txId = getFirstTransactionId(MEMBER, "Starbucks");
        mvc.perform(patch("/api/transactions/" + txId + "/category").with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + catManual + "\"}"))
                .andExpect(status().isNoContent());

        // Apply rules — should NOT overwrite the manual override
        mvc.perform(post("/api/category-rules/apply").with(as(MEMBER))).andExpect(status().isOk());

        mvc.perform(get("/api/transactions").with(as(MEMBER)))
                .andExpect(jsonPath("$[?(@.payee=='Starbucks')].categoryId").value(hasItem(catManual)))
                .andExpect(jsonPath("$[?(@.payee=='Starbucks')].categoryOverride").value(hasItem(true)));
    }

    @Test
    void syncAutoCategorisesNewTransactions() throws Exception {
        String catIncome = createCategory(MEMBER, "My Income", "#10B981", "💰");
        createRule(MEMBER, catIncome, 0, "AMOUNT_GTE", "0");

        connectAndSync(MEMBER);

        // Paycheck (amount = 2000, positive) should be auto-categorised as My Income
        mvc.perform(get("/api/transactions").with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.payee=='Employer Inc')].categoryId").value(hasItem(catIncome)));
    }

    @Test
    void ruleDoesNotAffectOtherHousehold() throws Exception {
        connectAndSync(MEMBER);
        String catId = createCategory(MEMBER, "Coffee", "#7C3AED", "☕");
        createRule(MEMBER, catId, 0, "PAYEE_CONTAINS", "Starbucks");
        mvc.perform(post("/api/category-rules/apply").with(as(MEMBER))).andExpect(status().isOk());

        // OTHER household has no transactions and should not be affected
        mvc.perform(get("/api/transactions").with(as(OTHER)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- rule priority update ---------------------------------------------------

    @Test
    void updateRulePriority() throws Exception {
        String catId = createCategory(MEMBER, "Coffee", "#7C3AED", "☕");
        String ruleId = createRule(MEMBER, catId, 5, "PAYEE_CONTAINS", "Starbucks");

        mvc.perform(patch("/api/category-rules/" + ruleId + "/priority").with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(1));
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

    private String createRule(UUID userId, String categoryId, int priority,
                               String matchField, String matchValue) throws Exception {
        MvcResult r = mvc.perform(post("/api/category-rules").with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId
                                + "\",\"priority\":" + priority
                                + ",\"matchField\":\"" + matchField
                                + "\",\"matchValue\":\"" + matchValue + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String getFirstTransactionId(UUID userId, String payee) throws Exception {
        MvcResult r = mvc.perform(get("/api/transactions").with(as(userId)))
                .andExpect(status().isOk()).andReturn();
        JsonNode txs = mapper.readTree(r.getResponse().getContentAsString());
        for (JsonNode tx : txs) {
            if (payee.equals(tx.path("payee").asText())) {
                return tx.get("id").asText();
            }
        }
        throw new IllegalStateException("Transaction with payee '" + payee + "' not found");
    }
}
