package com.budgettracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class BudgetTrackerApplicationTests extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void everythingElseRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
    }
}
