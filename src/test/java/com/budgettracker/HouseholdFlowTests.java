package com.budgettracker;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgettracker.application.port.HouseholdRepository;
import com.budgettracker.domain.Household;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class HouseholdFlowTests extends IntegrationTestBase {

    static final UUID OWNER = UUID.fromString(OWNER_ID);
    static final UUID PARTNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID STRANGER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID OTHER_HOUSEHOLD_USER = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    HouseholdRepository households;

    static RequestPostProcessor asUser(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@example.com"));
    }

    // ---- authentication -------------------------------------------------------------

    @Test
    void apiRequiresAToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/households")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/households/invites")).andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUserWithoutHouseholdSeesNothingBut_Me() throws Exception {
        mvc.perform(get("/api/me").with(asUser(STRANGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(STRANGER.toString()))
                .andExpect(jsonPath("$.household").value(nullValue()))
                .andExpect(jsonPath("$.canCreateHousehold").value(false));

        mvc.perform(get("/api/households/invites").with(asUser(STRANGER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_HOUSEHOLD"));
        mvc.perform(post("/api/households/invites").with(asUser(STRANGER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_HOUSEHOLD"));
    }

    // ---- first household ------------------------------------------------------------

    @Test
    void onlyTheConfiguredOwnerCanCreateTheFirstHousehold() throws Exception {
        mvc.perform(post("/api/households").with(asUser(STRANGER))).andExpect(status().isForbidden());

        mvc.perform(get("/api/me").with(asUser(OWNER)))
                .andExpect(jsonPath("$.canCreateHousehold").value(true));
        mvc.perform(post("/api/households").with(asUser(OWNER))).andExpect(status().isCreated());

        mvc.perform(post("/api/households").with(asUser(OWNER))).andExpect(status().isConflict());
        mvc.perform(get("/api/me").with(asUser(OWNER)))
                .andExpect(jsonPath("$.canCreateHousehold").value(false))
                .andExpect(jsonPath("$.household.members", hasSize(1)));
    }

    // ---- invites --------------------------------------------------------------------

    @Test
    void partnerJoinsWithAnInviteAndItWorksOnlyOnce() throws Exception {
        createHousehold();
        String token = createInvite();

        mvc.perform(redeem(PARTNER, token)).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(asUser(PARTNER)))
                .andExpect(jsonPath("$.household.members", hasSize(2)));
        mvc.perform(get("/api/me").with(asUser(OWNER)))
                .andExpect(jsonPath("$.household.members", hasSize(2)));

        // Single use: a third person cannot reuse the same link.
        mvc.perform(redeem(STRANGER, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE"));

        mvc.perform(get("/api/households/invites").with(asUser(OWNER)))
                .andExpect(jsonPath("$[0].status").value("USED"));
    }

    @Test
    void inviteTokenIsStoredOnlyAsAHash() throws Exception {
        createHousehold();
        String token = createInvite();

        Integer rawMatches = jdbc.queryForObject(
                "select count(*) from budget.household_invite where token_hash = ?", Integer.class, token);
        String stored = jdbc.queryForObject("select token_hash from budget.household_invite", String.class);

        org.junit.jupiter.api.Assertions.assertEquals(0, rawMatches);
        org.junit.jupiter.api.Assertions.assertNotEquals(token, stored);
        org.junit.jupiter.api.Assertions.assertEquals(64, stored.length());
    }

    @Test
    void revokedInviteCannotBeRedeemed() throws Exception {
        createHousehold();
        JsonNode created = createInviteJson();

        mvc.perform(delete("/api/households/invites/" + created.get("id").asText()).with(asUser(OWNER)))
                .andExpect(status().isNoContent());
        mvc.perform(redeem(PARTNER, created.get("token").asText()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void existingMemberCannotRedeemAnotherInvite() throws Exception {
        createHousehold();
        mvc.perform(redeem(PARTNER, createInvite())).andExpect(status().isOk());
        mvc.perform(redeem(PARTNER, createInvite())).andExpect(status().isConflict());
    }

    @Test
    void garbageAndBlankTokensAreRejected() throws Exception {
        mvc.perform(redeem(STRANGER, "not-a-real-token")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/invites/redeem").with(asUser(STRANGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    // ---- isolation between households -------------------------------------------------

    @Test
    void oneHouseholdCannotSeeOrTouchAnother() throws Exception {
        createHousehold();
        JsonNode ownerInvite = createInviteJson();

        // A second, completely separate household with its own member.
        mvc.perform(get("/api/me").with(asUser(OTHER_HOUSEHOLD_USER))).andExpect(status().isOk());
        Household other = households.create(new Household(UUID.randomUUID(), "Someone else"));
        households.addMember(other.id(), OTHER_HOUSEHOLD_USER, Instant.now());

        mvc.perform(get("/api/households/invites").with(asUser(OTHER_HOUSEHOLD_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/households/invites/" + ownerInvite.get("id").asText())
                        .with(asUser(OTHER_HOUSEHOLD_USER)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me").with(asUser(OTHER_HOUSEHOLD_USER)))
                .andExpect(jsonPath("$.household.name").value("Someone else"))
                .andExpect(jsonPath("$.household.members", hasSize(1)));

        // The owner's invite is untouched.
        mvc.perform(get("/api/households/invites").with(asUser(OWNER)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    // ---- helpers --------------------------------------------------------------------

    private void createHousehold() throws Exception {
        mvc.perform(post("/api/households").with(asUser(OWNER))).andExpect(status().isCreated());
    }

    private JsonNode createInviteJson() throws Exception {
        String body = mvc.perform(post("/api/households/invites").with(asUser(OWNER)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String createInvite() throws Exception {
        return createInviteJson().get("token").asText();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder redeem(UUID user, String token) {
        return post("/api/invites/redeem").with(asUser(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }
}
