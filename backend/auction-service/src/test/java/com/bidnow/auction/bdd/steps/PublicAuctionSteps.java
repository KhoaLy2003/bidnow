package com.bidnow.auction.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import com.bidnow.bdd.wiremock.WireMockSupport;
import io.cucumber.java.Before;
import io.cucumber.java.en.When;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

@RequiredArgsConstructor
public class PublicAuctionSteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;

    @Before
    public void setUpWireMock() {
        WireMockSupport.reset();
        WireMockSupport.SERVER.stubFor(
                get(urlMatching("/api/v1/users/internal/.*/summary"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"status\":200,\"message\":null," +
                                        "\"data\":{\"id\":\"550e8400-e29b-41d4-a716-446655440001\"," +
                                        "\"name\":\"Test Seller\",\"avatarUrl\":null}}"))
        );
    }

    @When("a public request is made to browse auctions")
    public void browseAuctions() {
        ctx.setLastResponse(client.given().get("/api/v1/auctions/public"));
    }

    @When("a public request is made to browse auctions with params {string}")
    public void browseAuctionsWithParams(String params) {
        RequestSpecification spec = client.given();
        for (String param : params.split("&")) {
            String[] kv = param.split("=", 2);
            spec.param(kv[0], kv[1]);
        }
        ctx.setLastResponse(spec.get("/api/v1/auctions/public"));
    }

    @When("a public request is made to get auction {string}")
    public void getAuctionById(String id) {
        ctx.setLastResponse(client.given().get("/api/v1/auctions/public/" + id));
    }

    @When("a public request is made to get category auction counts")
    public void getCategoryCounts() {
        ctx.setLastResponse(client.given().get("/api/v1/auctions/public/category-counts"));
    }
}
