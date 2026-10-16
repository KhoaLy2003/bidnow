// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/MyActiveBidsSteps.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class MyActiveBidsSteps {

    private static final String SUMMARIES_PATH = "/api/v1/internal/auctions/bid-summaries";

    private final BddRestClient client;
    private final ScenarioContext ctx;
    private final JdbcTemplate jdbcTemplate;

    @Given("user {string} has no recorded bids")
    public void noRecordedBids(String userId) {
        jdbcTemplate.update("DELETE FROM bids WHERE bidder_id IN (?::uuid, ?::uuid)",
                userId, "550e8400-e29b-41d4-a716-446655440021");
    }

    @Given("auction-service reports these active auctions:")
    public void auctionServiceReports(DataTable table) {
        String items = table.asMaps().stream().map(MyActiveBidsSteps::summaryJson).collect(Collectors.joining(","));
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(SUMMARIES_PATH))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":200,\"message\":\"Success\",\"data\":[" + items + "]}")));
    }

    @Given("auction-service fails when asked for bid summaries")
    public void auctionServiceFails() {
        WireMockSupport.SERVER.stubFor(post(urlEqualTo(SUMMARIES_PATH)).willReturn(aResponse().withStatus(500)));
    }

    @When("user {string} requests their active bids")
    public void requestActiveBids(String userId) {
        ctx.setLastResponse(client.given().header("X-User-Id", userId).get("/api/v1/bids/me/active"));
    }

    @When("an anonymous user requests their active bids")
    public void anonymousRequestActiveBids() {
        ctx.setLastResponse(client.given().get("/api/v1/bids/me/active"));
    }

    @Then("the active bid auctions should be {string}")
    public void activeBidAuctions(String csv) {
        List<String> ids = ctx.getLastResponse().jsonPath().getList("data.items.auctionId", String.class);
        assertThat(ids).containsExactly(csv.split(","));
    }

    @Then("auction-service should have received {int} bid summary request(s)")
    public void summaryCalls(int count) {
        WireMockSupport.SERVER.verify(count, postRequestedFor(urlEqualTo(SUMMARIES_PATH)));
    }

    private static String summaryJson(Map<String, String> row) {
        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC).plusHours(Long.parseLong(row.get("hoursLeft")));
        return "{\"auctionId\":\"" + row.get("auctionId") + "\",\"title\":\"" + row.get("title")
                + "\",\"primaryImageUrl\":null,\"currentPrice\":" + row.get("currentPrice")
                + ",\"currentWinnerId\":\"" + row.get("winnerId") + "\",\"totalBids\":3,\"endTime\":\"" + endTime + "\"}";
    }
}
