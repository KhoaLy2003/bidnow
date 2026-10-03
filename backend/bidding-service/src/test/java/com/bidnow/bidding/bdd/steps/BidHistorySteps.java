// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/BidHistorySteps.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class BidHistorySteps {

    private static final String SUMMARIES_PATH = "/api/v1/users/internal/summaries";

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;

    @Before("@history")
    public void clearHistoryAuctions() {
        jdbcTemplate.update("DELETE FROM bids WHERE auction_id IN (?::uuid, ?::uuid, ?::uuid)",
                "e0000000-0000-0000-0000-000000000001", "e0000000-0000-0000-0000-000000000002",
                "e0000000-0000-0000-0000-000000000003");
    }

    @Given("bidder {string} placed a bid of {string} on auction {string} {int} minutes ago")
    public void storedBid(String bidderId, String amount, String auctionId, int minutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO bids (id, auction_id, bidder_id, amount, is_auto_bid, is_anti_sniping_triggered, created_at, updated_at)
                VALUES (?, ?::uuid, ?::uuid, ?, false, false, NOW() - (? * INTERVAL '1 minute'), NOW() - (? * INTERVAL '1 minute'))
                """, UUID.randomUUID(), auctionId, bidderId, new BigDecimal(amount), minutesAgo, minutesAgo);
    }

    @Given("user-service resolves user {string} as {string} in batch")
    public void userServiceBatch(String userId, String name) {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(SUMMARIES_PATH))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":200,\"message\":\"Success\",\"data\":[{\"id\":\"" + userId
                                + "\",\"name\":\"" + name + "\",\"avatarUrl\":null}]}")));
    }

    @When("an anonymous user requests the bid history of auction {string}")
    public void anonymousHistory(String auctionId) {
        ctx.setLastResponse(client.given().get("/api/v1/bids/auction/" + auctionId));
    }

    @When("user {string} requests their bids on auction {string}")
    public void myBids(String userId, String auctionId) {
        ctx.setLastResponse(client.given().header("X-User-Id", userId)
                .get("/api/v1/bids/auction/" + auctionId + "/my-bids"));
    }

    @When("an anonymous user requests their bids on auction {string}")
    public void anonymousMyBids(String auctionId) {
        ctx.setLastResponse(client.given().get("/api/v1/bids/auction/" + auctionId + "/my-bids"));
    }

    @Then("the history amounts should be {string}")
    public void historyAmounts(String csv) {
        List<String> amounts = ctx.getLastResponse().jsonPath().getList("data.data.amount", Object.class)
                .stream().map(a -> new BigDecimal(a.toString()).setScale(2).toPlainString()).toList();
        assertThat(amounts).containsExactly(csv.split(","));
    }

    @Then("user-service should have received {int} batch summary request(s)")
    public void batchCalls(int count) {
        WireMockSupport.SERVER.verify(count, postRequestedFor(urlEqualTo(SUMMARIES_PATH)));
    }
}
