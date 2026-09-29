// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/BidPlacementSteps.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class BidPlacementSteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Before
    public void resetExternalState() {
        WireMockSupport.reset();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Given("auction-service has auction {string} with status {string}, price {string}, increment {string}, {int} bids and seller {string}")
    public void stubBidContext(String auctionId, String status, String price, String increment,
                               int totalBids, String sellerId) {
        String endTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).toString();
        String body = """
                {"status":200,"message":"Success","data":{
                  "auctionId":"%s","title":"BDD Auction","sellerId":"%s","status":"%s",
                  "currentPrice":%s,"bidIncrement":%s,"depositAmount":20.00,
                  "currentWinnerId":null,"totalBids":%d,"endTime":"%s"}}
                """.formatted(auctionId, sellerId, status, price, increment, totalBids, endTime);
        WireMockSupport.SERVER.stubFor(get(urlEqualTo(contextPath(auctionId)))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Given("auction-service responds {int} for the bid context of auction {string}")
    public void stubBidContextError(int status, String auctionId) {
        WireMockSupport.SERVER.stubFor(get(urlEqualTo(contextPath(auctionId)))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":" + status + ",\"errorCode\":\"X\",\"message\":\"stub\"}")));
    }

    @When("user {string} bids {string} on auction {string}")
    public void userBids(String userId, String amount, String auctionId) {
        ctx.setLastResponse(client.given()
                .header("X-User-Id", userId)
                .body(Map.of("auctionId", auctionId, "amount", new BigDecimal(amount)))
                .post("/api/v1/bids"));
    }

    @When("an unauthenticated request bids {string} on auction {string}")
    public void unauthenticatedBid(String amount, String auctionId) {
        ctx.setLastResponse(client.given()
                .body(Map.of("auctionId", auctionId, "amount", new BigDecimal(amount)))
                .post("/api/v1/bids"));
    }

    @Then("auction-service should have received {int} bid-context request(s) for auction {string}")
    public void verifyContextCalls(int count, String auctionId) {
        WireMockSupport.SERVER.verify(count, getRequestedFor(urlEqualTo(contextPath(auctionId))));
    }

    @Then("the bid context for auction {string} should be cached in Redis")
    public void contextCached(String auctionId) {
        assertThat(redisTemplate.opsForValue().get("bidding:auction:" + auctionId + ":context")).isNotNull();
        assertThat(redisTemplate.getExpire("bidding:auction:" + auctionId + ":context")).isPositive();
    }

    @Then("the bids table should exist with its indexes")
    public void bidsSchemaExists() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'bids'", String.class);
        assertThat(columns).contains("id", "auction_id", "bidder_id", "amount", "is_auto_bid",
                "is_anti_sniping_triggered", "created_at", "updated_at");
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'bids'", String.class);
        assertThat(indexes).contains("idx_bids_auction_created", "idx_bids_auction_amount",
                "idx_bids_bidder_auction_created");
    }

    private static String contextPath(String auctionId) {
        return "/api/v1/internal/auctions/" + auctionId + "/bid-context";
    }
}
