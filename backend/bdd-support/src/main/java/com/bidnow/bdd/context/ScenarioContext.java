package com.bidnow.bdd.context;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
@ScenarioScope
public class ScenarioContext {
    private Response lastResponse;
    private String authToken;
    private String currentEmail;
}
