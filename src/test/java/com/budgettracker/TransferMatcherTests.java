package com.budgettracker;

import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class TransferMatcherTests extends IntegrationTestBase {

    static final UUID MEMBER      = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    static final String SETUP_TOKEN = "dGVzdC10cmFuc2Zlcg==";
    static final String ACCESS_URL  = "https://user:pass@beta-bridge.simplefin.org/simplefin";

    // Two accounts in the same household
    static final String ACC_CHECKING = "acc_transfer_checking";
    static final String ACC_SAVINGS  = "acc_transfer_savings";

    static final LocalDate TX_DATE = LocalDate.of(2026, 10, 1);

    @Autowired MockMvc mvc;
    @Autowired HouseholdRepository households;

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO budget.app_user (id, email) VALUES (?, ?) ON CONFLICT DO NOTHING",
                MEMBER, MEMBER + "@example.com");

        Household h = households.create(new Household(UUID.randomUUID(), "Transfer Household"));
        households.addMember(h.id(), MEMBER, Instant.now());

        when(bankData.claim(eq(SETUP_TOKEN))).thenReturn(ACCESS_URL);
        when(bankData.fetchAccounts(eq(ACCESS_URL))).thenReturn(List.of(
                new BankDataProvider.ProviderAccount(ACC_CHECKING, ACC_CHECKING,
                        "Chase", "Checking", "depository", "checking", "1111", "USD", "open",
                        new BigDecimal("5000"), new BigDecimal("5000")),
                new BankDataProvider.ProviderAccount(ACC_SAVINGS, ACC_SAVINGS,
                        "Chase", "Savings", "depository", "savings", "2222", "USD", "open",
                        new BigDecimal("3000"), new BigDecimal("3000"))));
    }

    static RequestPostProcessor as(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@example.com"));
    }

    private void connect() throws Exception {
        mvc.perform(post("/api/simplefin/connections").with(as(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setupToken\":\"" + SETUP_TOKEN + "\"}"))
                .andExpect(status().isCreated());
    }

    // ---- exact pair matched -----------------------------------------------------

    @Test
    void exactTransferPairMarkedAsInternal() throws Exception {
        connect();
        when(bankData.fetchTransactionsWithBalances(eq(ACCESS_URL), any())).thenReturn(
                new BankDataProvider.SyncResult(
                        List.of(
                                new BankDataProvider.ProviderAccount(ACC_CHECKING, ACC_CHECKING,
                                        "Chase", "Checking", "depository", "checking", "1111", "USD", "open",
                                        new BigDecimal("4500"), new BigDecimal("4500")),
                                new BankDataProvider.ProviderAccount(ACC_SAVINGS, ACC_SAVINGS,
                                        "Chase", "Savings", "depository", "savings", "2222", "USD", "open",
                                        new BigDecimal("3500"), new BigDecimal("3500"))),
                        List.of(
                                new BankDataProvider.ProviderTransaction(
                                        "tx-transfer-out", ACC_CHECKING, new BigDecimal("-500.00"), "USD",
                                        "TRANSFER TO SAVINGS", "Transfer", null, TX_DATE, null, false),
                                new BankDataProvider.ProviderTransaction(
                                        "tx-transfer-in", ACC_SAVINGS, new BigDecimal("500.00"), "USD",
                                        "TRANSFER FROM CHECKING", "Transfer", null, TX_DATE, null, false))));

        mvc.perform(post("/api/sync").with(as(MEMBER))).andExpect(status().isOk());

        // Both transactions must be flagged as internal transfers
        mvc.perform(get("/api/transactions").with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.isInternalTransfer==true)]", hasSize(2)));
    }

    // ---- one-sided: only one account connected ----------------------------------

    @Test
    void oneSidedTransactionRemainsRegular() throws Exception {
        connect();
        // Only transactions on one account — nothing to pair with
        when(bankData.fetchTransactionsWithBalances(eq(ACCESS_URL), any())).thenReturn(
                new BankDataProvider.SyncResult(
                        List.of(
                                new BankDataProvider.ProviderAccount(ACC_CHECKING, ACC_CHECKING,
                                        "Chase", "Checking", "depository", "checking", "1111", "USD", "open",
                                        new BigDecimal("4500"), new BigDecimal("4500")),
                                new BankDataProvider.ProviderAccount(ACC_SAVINGS, ACC_SAVINGS,
                                        "Chase", "Savings", "depository", "savings", "2222", "USD", "open",
                                        new BigDecimal("3000"), new BigDecimal("3000"))),
                        List.of(
                                new BankDataProvider.ProviderTransaction(
                                        "tx-out-only", ACC_CHECKING, new BigDecimal("-500.00"), "USD",
                                        "ACH TRANSFER", "Transfer", null, TX_DATE, null, false))));

        mvc.perform(post("/api/sync").with(as(MEMBER))).andExpect(status().isOk());

        // No counterpart → stays as regular outflow (isInternalTransfer=false)
        mvc.perform(get("/api/transactions").with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.isInternalTransfer==false)]", hasSize(1)));
    }

    // ---- ambiguous: two equal amounts in window ---------------------------------

    @Test
    void ambiguousTransfersSkipped() throws Exception {
        connect();
        // Two +$100 on savings for one -$100 on checking — ambiguous, skip all three
        when(bankData.fetchTransactionsWithBalances(eq(ACCESS_URL), any())).thenReturn(
                new BankDataProvider.SyncResult(
                        List.of(
                                new BankDataProvider.ProviderAccount(ACC_CHECKING, ACC_CHECKING,
                                        "Chase", "Checking", "depository", "checking", "1111", "USD", "open",
                                        new BigDecimal("4900"), new BigDecimal("4900")),
                                new BankDataProvider.ProviderAccount(ACC_SAVINGS, ACC_SAVINGS,
                                        "Chase", "Savings", "depository", "savings", "2222", "USD", "open",
                                        new BigDecimal("3200"), new BigDecimal("3200"))),
                        List.of(
                                new BankDataProvider.ProviderTransaction(
                                        "tx-out", ACC_CHECKING, new BigDecimal("-100.00"), "USD",
                                        "TRANSFER", "Transfer", null, TX_DATE, null, false),
                                new BankDataProvider.ProviderTransaction(
                                        "tx-in-1", ACC_SAVINGS, new BigDecimal("100.00"), "USD",
                                        "TRANSFER A", "Transfer A", null, TX_DATE, null, false),
                                new BankDataProvider.ProviderTransaction(
                                        "tx-in-2", ACC_SAVINGS, new BigDecimal("100.00"), "USD",
                                        "TRANSFER B", "Transfer B", null, TX_DATE, null, false))));

        mvc.perform(post("/api/sync").with(as(MEMBER))).andExpect(status().isOk());

        // All three remain as regular transactions (ambiguous — skip)
        mvc.perform(get("/api/transactions").with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.isInternalTransfer==true)]", hasSize(0)));
    }

    // ---- idempotency ------------------------------------------------------------

    @Test
    void transferDetectionIsIdempotent() throws Exception {
        connect();
        when(bankData.fetchTransactionsWithBalances(eq(ACCESS_URL), any())).thenReturn(
                new BankDataProvider.SyncResult(
                        List.of(
                                new BankDataProvider.ProviderAccount(ACC_CHECKING, ACC_CHECKING,
                                        "Chase", "Checking", "depository", "checking", "1111", "USD", "open",
                                        new BigDecimal("4500"), new BigDecimal("4500")),
                                new BankDataProvider.ProviderAccount(ACC_SAVINGS, ACC_SAVINGS,
                                        "Chase", "Savings", "depository", "savings", "2222", "USD", "open",
                                        new BigDecimal("3500"), new BigDecimal("3500"))),
                        List.of(
                                new BankDataProvider.ProviderTransaction(
                                        "tx-idem-out", ACC_CHECKING, new BigDecimal("-200.00"), "USD",
                                        "TRANSFER", "Transfer", null, TX_DATE, null, false),
                                new BankDataProvider.ProviderTransaction(
                                        "tx-idem-in", ACC_SAVINGS, new BigDecimal("200.00"), "USD",
                                        "TRANSFER", "Transfer", null, TX_DATE, null, false))));

        mvc.perform(post("/api/sync").with(as(MEMBER))).andExpect(status().isOk());
        mvc.perform(post("/api/sync").with(as(MEMBER))).andExpect(status().isOk());

        // Still exactly 2 internal transfers after two syncs
        mvc.perform(get("/api/transactions").with(as(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.isInternalTransfer==true)]", hasSize(2)));
    }
}
