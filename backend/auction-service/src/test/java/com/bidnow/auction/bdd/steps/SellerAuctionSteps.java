// backend/auction-service/src/test/java/com/bidnow/auction/bdd/steps/SellerAuctionSteps.java
package com.bidnow.auction.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.Before;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class SellerAuctionSteps {

    private static final String DRAFT_ID = "b0000000-0000-0000-0000-000000000001";
    private static final String SCHEDULED_ID = "b0000000-0000-0000-0000-000000000002";
    private static final String ACTIVE_ID = "b0000000-0000-0000-0000-000000000003";
    private static final String CANCELLED_ID = "b0000000-0000-0000-0000-000000000004";

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;

    @Before
    public void resetAuctions() {
        // Pattern: inline fixed UUIDs directly (no user input — same style as user-service @Before hooks)
        jdbcTemplate.update(
                "DELETE FROM auction_status_history " +
                        "WHERE auction_id IN ('b0000000-0000-0000-0000-000000000001'::uuid," +
                        " 'b0000000-0000-0000-0000-000000000002'::uuid," +
                        " 'b0000000-0000-0000-0000-000000000003'::uuid," +
                        " 'b0000000-0000-0000-0000-000000000004'::uuid)"
        );
        jdbcTemplate.update(
                "UPDATE auction_items SET status = 'DRAFT', deleted_at = NULL, " +
                        "cancellation_reason = NULL, cancelled_by = NULL, cancelled_at = NULL, " +
                        "rejection_reason = NULL, rejected_by = NULL, rejected_at = NULL, " +
                        "winner_id = NULL, completed_at = NULL " +
                        "WHERE id = 'b0000000-0000-0000-0000-000000000001'::uuid"
        );
        jdbcTemplate.update(
                "UPDATE auction_items SET status = 'SCHEDULED', " +
                        "rejection_reason = NULL, rejected_by = NULL, rejected_at = NULL, " +
                        "cancellation_reason = NULL, cancelled_by = NULL, cancelled_at = NULL " +
                        "WHERE id = 'b0000000-0000-0000-0000-000000000002'::uuid"
        );
        jdbcTemplate.update(
                "UPDATE auction_items SET status = 'ACTIVE', " +
                        "cancellation_reason = NULL, cancelled_by = NULL, cancelled_at = NULL, " +
                        "winner_id = NULL, completed_at = NULL, " +
                        "total_bids = 1, current_winner_id = '550e8400-e29b-41d4-a716-446655440002'::uuid " +
                        "WHERE id = 'b0000000-0000-0000-0000-000000000003'::uuid"
        );
        jdbcTemplate.update(
                "UPDATE auction_items SET status = 'CANCELLED' " +
                        "WHERE id = 'b0000000-0000-0000-0000-000000000004'::uuid"
        );
    }

    // ── Create ───────────────────────────────────────────────────────────────

    // DocString body comes from the feature file (the trailing String param receives the """ block)
    @When("seller {string} creates a new auction with body:")
    public void sellerCreatesAuction(String sellerId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .body(body)
                        .post("/api/v1/auctions")
        );
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @When("seller {string} updates auction {string} with body:")
    public void sellerUpdatesAuction(String sellerId, String auctionId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .body(body)
                        .put("/api/v1/auctions/" + auctionId)
        );
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @When("seller {string} deletes auction {string}")
    public void sellerDeletesAuction(String sellerId, String auctionId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .delete("/api/v1/auctions/" + auctionId)
        );
    }

    // ── Publish ──────────────────────────────────────────────────────────────

    @When("seller {string} publishes auction {string}")
    public void sellerPublishesAuction(String sellerId, String auctionId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .post("/api/v1/auctions/" + auctionId + "/publish")
        );
    }

    @When("seller {string} publishes the last created auction")
    public void sellerPublishesLastCreatedAuction(String sellerId) {
        String auctionId = ctx.getLastResponse().jsonPath().getString("data.id");
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .post("/api/v1/auctions/" + auctionId + "/publish")
        );
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    @When("seller {string} cancels auction {string} with body:")
    public void sellerCancelsAuction(String sellerId, String auctionId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .body(body)
                        .post("/api/v1/auctions/" + auctionId + "/cancel")
        );
    }

    // ── My Auctions ──────────────────────────────────────────────────────────

    @When("an unauthenticated request is made to get my auctions")
    public void unauthenticatedGetMyAuctions() {
        ctx.setLastResponse(client.given().get("/api/v1/auctions/me"));
    }

    @When("seller {string} requests their own auctions")
    public void sellerRequestsOwnAuctions(String sellerId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", sellerId)
                        .header("X-User-Roles", "USER")
                        .get("/api/v1/auctions/me")
        );
    }
}
