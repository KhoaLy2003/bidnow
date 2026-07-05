package com.bidnow.bdd.client;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Lazy
@Component
public class BddRestClient {

    @Value("${local.server.port}")
    private int port;

    public RequestSpecification given() {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(ContentType.JSON);
    }
}
