package com.bidnow.auction.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdminAuctionSteps {

    private static final String ADMIN_ID = "550e8400-e29b-41d4-a716-000000000001";

    private final BddRestClient client;
    private final ScenarioContext ctx;

    @When("an admin lists all auctions")
    public void adminListsAllAuctions() {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .get("/api/v1/admin/auctions")
        );
    }

    @When("an admin gets auction detail for {string}")
    public void adminGetsAuctionDetail(String auctionId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .get("/api/v1/admin/auctions/" + auctionId)
        );
    }

    // DocString body from the feature file (""" block)
    @When("an admin rejects auction {string} with body:")
    public void adminRejectsAuction(String auctionId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .body(body)
                        .post("/api/v1/admin/auctions/" + auctionId + "/reject")
        );
    }

    @When("an admin cancels auction {string} with body:")
    public void adminCancelsAuction(String auctionId, String body) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .body(body)
                        .post("/api/v1/admin/auctions/" + auctionId + "/cancel")
        );
    }

    @When("an admin force-closes auction {string}")
    public void adminForceClosesAuction(String auctionId) {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .post("/api/v1/admin/auctions/" + auctionId + "/force-close")
        );
    }

    @When("a non-admin user lists all auctions")
    public void nonAdminListsAllAuctions() {
        ctx.setLastResponse(
                client.given()
                        .header("X-User-Id", "550e8400-e29b-41d4-a716-446655440001")
                        .header("X-User-Roles", "USER")
                        .get("/api/v1/admin/auctions")
        );
    }
}
