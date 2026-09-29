// backend/bidding-service/src/test/java/com/bidnow/bidding/bdd/steps/CommonSteps.java
package com.bidnow.bidding.bdd.steps;

import com.bidnow.bdd.context.ScenarioContext;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
public class CommonSteps {

    private final ScenarioContext ctx;

    @Then("the response status should be {int}")
    public void assertStatus(int expectedStatus) {
        assertThat(ctx.getLastResponse().statusCode())
                .as("Expected HTTP status %d but got %d. Body: %s",
                        expectedStatus,
                        ctx.getLastResponse().statusCode(),
                        ctx.getLastResponse().asString())
                .isEqualTo(expectedStatus);
    }

    @Then("the response field {string} should equal {string}")
    public void assertFieldEquals(String jsonPath, String expectedValue) {
        String actual = ctx.getLastResponse().jsonPath().getString(jsonPath);
        assertThat(actual)
                .as("Expected field '%s' to equal '%s' but was '%s'", jsonPath, expectedValue, actual)
                .isEqualTo(expectedValue);
    }
}
