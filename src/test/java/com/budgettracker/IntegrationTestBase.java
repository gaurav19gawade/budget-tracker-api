package com.budgettracker;

import com.budgettracker.application.port.BankDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared setup: one real Postgres (same major version as Supabase) for all integration tests,
 * with the production security chain active (JWTs are supplied by Spring Security's test support).
 */
@SpringBootTest(properties = {
        "app.supabase.jwks-uri=http://localhost:1/jwks.json",
        "app.supabase.issuer=http://localhost/auth/v1",
        "app.bootstrap-owner-user-id=" + IntegrationTestBase.OWNER_ID,
        // 32 zero-bytes as base64 — test-only AES key for TokenEncryptionService.
        "app.teller.token-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    BankDataProvider bankData;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE budget.transaction, budget.category_rule, budget.category, "
                + "budget.bank_account, budget.teller_enrollment, "
                + "budget.household_invite, budget.household_member, "
                + "budget.household, budget.app_user CASCADE");
    }
}
