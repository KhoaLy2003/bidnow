package com.bidnow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ="
})
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routesLoad_andWebsocketRouteHasDefaultFilter() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).extracting(Route::getId)
                .contains("media-websocket", "bidding-service", "media-service");
        Route ws = routes.stream().filter(r -> r.getId().equals("media-websocket")).findFirst().orElseThrow();
        assertThat(ws.getFilters()).isNotEmpty();
        assertThat(ws.getFilters()).anyMatch(f -> f.toString().toLowerCase().contains("deduperesponseheader"));
    }
}
