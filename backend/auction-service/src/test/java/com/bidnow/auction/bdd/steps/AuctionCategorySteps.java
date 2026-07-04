package com.bidnow.auction.bdd.steps;

import com.bidnow.bdd.client.BddRestClient;
import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuctionCategorySteps {

    private final BddRestClient client;
    private final ScenarioContext ctx;

    @When("a request is made to get all auction categories")
    public void getCategories() {
        ctx.setLastResponse(client.given().get("/api/v1/categories"));
    }
}
