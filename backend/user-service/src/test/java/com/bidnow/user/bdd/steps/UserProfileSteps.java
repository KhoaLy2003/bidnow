package com.bidnow.user.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.Before;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

@RequiredArgsConstructor
public class UserProfileSteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;

    @Before
    public void cleanProfiles() {
        // Reset to known state rather than delete — Liquibase pre-seeds these rows and won't re-insert them.
        jdbcTemplate.update("""
                UPDATE user_profiles
                SET display_name = 'Test User', updated_at = NOW()
                WHERE user_id IN ('550e8400-e29b-41d4-a716-446655440001'::uuid,
                                  '550e8400-e29b-41d4-a716-446655440002'::uuid)
                """);
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("user with id {string} requests their profile")
    public void userRequestsProfile(String userId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", userId)
                        .header("X-User-Roles", "USER")
                        .get("/api/v1/users/me")
        );
    }

    @When("an unauthenticated request is made to get profile")
    public void unauthenticatedGetProfile() {
        ctx.setLastResponse(
                client.given()
                        .get("/api/v1/users/me")
        );
    }

    @When("user with id {string} updates display name to {string}")
    public void userUpdatesDisplayName(String userId, String displayName) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", userId)
                        .header("X-User-Roles", "USER")
                        .body(Map.of("displayName", displayName))
                        .put("/api/v1/users/me")
        );
    }
}
