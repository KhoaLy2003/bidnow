// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/BidPlacementSteps.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class BidPlacementSteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

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

    @Given("wallet-service locks deposits successfully")
    public void walletLocksDeposits() {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(WALLET_LOCK_PATH))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":200,"message":"Success","data":{"lockId":"%s","amount":20.00,
                                 "status":"LOCKED","alreadyLocked":false,"availableBalance":80.00,"lockedBalance":20.00}}
                                """.formatted(UUID.randomUUID()))));
    }

    @Given("wallet-service rejects deposit locks for insufficient balance")
    public void walletInsufficientBalance() {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(WALLET_LOCK_PATH))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":400,"errorCode":"INSUFFICIENT_BALANCE","message":"Insufficient balance",
                                 "errors":{"availableBalance":"10.00","required":"20.00"}}
                                """)));
    }

    @Given("wallet-service responds {int} to deposit locks")
    public void walletResponds(int status) {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(WALLET_LOCK_PATH))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":" + status + ",\"errorCode\":\"X\",\"message\":\"stub\"}")));
    }

    @Given("auction-service applies bids on auction {string} returning price {string} and {int} total bids")
    public void auctionAppliesBids(String auctionId, String price, int totalBids) {
        String body = """
                {"status":200,"message":"Success","data":{"auctionId":"%s","currentPrice":%s,
                 "currentWinnerId":"%s","previousWinnerId":null,"totalBids":%d,"endTime":"%s",
                 "extended":false,"extensionCount":0}}
                """.formatted(auctionId, price, UUID.randomUUID(), totalBids,
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(applyPath(auctionId)))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Given("auction-service rejects bids on auction {string} with {int} {string}")
    public void auctionRejectsBids(String auctionId, int status, String errorCode) {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(applyPath(auctionId)))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":" + status + ",\"errorCode\":\"" + errorCode + "\",\"message\":\"stub\"}")));
    }

    @Given("user-service knows user {string} as {string}")
    public void userServiceKnows(String userId, String name) {
        WireMockSupport.SERVER.stubFor(get(urlEqualTo("/api/v1/users/internal/" + userId + "/summary"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":200,\"message\":\"Success\",\"data\":{\"id\":\"" + userId
                                + "\",\"name\":\"" + name + "\",\"avatarUrl\":null}}")));
    }

    @Then("wallet-service should have received {int} deposit-lock request(s)")
    public void verifyWalletCalls(int count) {
        WireMockSupport.SERVER.verify(count, postRequestedFor(urlEqualTo(WALLET_LOCK_PATH)));
    }

    @Then("auction-service should have received {int} apply-bid request(s) for auction {string}")
    public void verifyApplyCalls(int count, String auctionId) {
        WireMockSupport.SERVER.verify(count, postRequestedFor(urlEqualTo(applyPath(auctionId))));
    }

    @Then("{int} bid(s) should be stored for auction {string}")
    public void storedBids(int count, String auctionId) {
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bids WHERE auction_id = ?::uuid", Integer.class, auctionId);
        assertThat(stored).isEqualTo(count);
    }

    private static final String WALLET_LOCK_PATH = "/api/v1/internal/wallet/deposit-lock";

    private static String applyPath(String auctionId) {
        return "/api/v1/internal/auctions/" + auctionId + "/bids";
    }

    private static String contextPath(String auctionId) {
        return "/api/v1/internal/auctions/" + auctionId + "/bid-context";
    }
}
