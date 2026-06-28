package com.bidnow.identity.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

@RequiredArgsConstructor
public class AuthSteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate.update("DELETE FROM identity_users WHERE email LIKE '%@example.com'");
        WireMockSupport.reset();
        WireMockSupport.SERVER.stubFor(
                post(urlEqualTo("/api/v1/users/internal/profiles"))
                        .willReturn(aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"status\":201,\"message\":\"Created\",\"data\":null}"))
        );
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @When("user registers with name {string} email {string} and password {string}")
    public void userRegisters(String name, String email, String password) {
        ctx.setLastResponse(
                client.given()
                        .body(Map.of("name", name, "email", email, "password", password))
                        .post("/api/v1/auth/register")
                        .andReturn()
        );
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @When("user logs in with email {string} and password {string}")
    public void userLogsIn(String email, String password) {
        ctx.setLastResponse(
                client.given()
                        .body(Map.of("email", email, "password", password))
                        .post("/api/v1/auth/login")
                        .andReturn()
        );
    }

    // ── OTP ───────────────────────────────────────────────────────────────────

    @Given("a user is registered and waiting for OTP with email {string}")
    public void aUserIsRegisteredAndWaitingForOtp(String email) {
        client.given()
                .body(Map.of("name", "OTP User", "email", email, "password", "P@ssw0rd1"))
                .post("/api/v1/auth/register");
    }

    @Given("the OTP for {string} has expired in the database")
    public void theOtpForHasExpiredInDatabase(String email) {
        jdbcTemplate.update(
                "UPDATE identity_users SET otp_expires_at = NOW() - INTERVAL '1 minute' WHERE email = ?",
                email
        );
    }

    @When("user submits the correct OTP for email {string}")
    public void userSubmitsCorrectOtp(String email) {
        String otp = jdbcTemplate.queryForObject(
                "SELECT verification_otp FROM identity_users WHERE email = ?",
                String.class, email
        );
        ctx.setLastResponse(
                client.given()
                        .body(Map.of("email", email, "otp", otp))
                        .post("/api/v1/auth/verify-otp")
                        .andReturn()
        );
    }

    @When("user submits an incorrect OTP {string} for email {string}")
    public void userSubmitsIncorrectOtp(String otp, String email) {
        ctx.setLastResponse(
                client.given()
                        .body(Map.of("email", email, "otp", otp))
                        .post("/api/v1/auth/verify-otp")
                        .andReturn()
        );
    }
}
