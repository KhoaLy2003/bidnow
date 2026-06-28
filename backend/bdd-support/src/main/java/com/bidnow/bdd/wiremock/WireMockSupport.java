package com.bidnow.bdd.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class WireMockSupport {

    public static final WireMockServer SERVER =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        SERVER.start();
    }

    public static void reset() {
        SERVER.resetAll();
    }

    public static String baseUrl() {
        return "http://localhost:" + SERVER.port();
    }
}
