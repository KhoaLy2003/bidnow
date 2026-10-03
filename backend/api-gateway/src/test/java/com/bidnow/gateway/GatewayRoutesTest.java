/*
 * BidNow Auction System
 */
package com.bidnow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against re-enabling the discovery locator, which auto-creates
 * {@code /{service-id}/**} routes exposing every downstream path (actuator,
 * internal endpoints) to any JWT holder. Services are registered via the simple
 * discovery client so the locator would produce routes if it were enabled.
 */
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.client.simple.instances.auction-service[0].uri=http://localhost:9001",
        "spring.cloud.discovery.client.simple.instances.bidding-service[0].uri=http://localhost:9002",
        "spring.cloud.discovery.client.simple.instances.identity-service[0].uri=http://localhost:9003"
})
class GatewayRoutesTest {

    private static final List<String> SERVICE_IDS = List.of(
            "identity-service", "auction-service", "user-service",
            "bidding-service", "wallet-service", "media-service");

    private static final String WEBSOCKET_ROUTE_ID = "media-websocket";

    @Autowired
    private RouteLocator routeLocator;

    private List<Route> routes() {
        return routeLocator.getRoutes().collectList().block();
    }

    @Test
    void noDiscoveryLocatorRoutesAreCreated() {
        assertThat(routes())
                .extracting(Route::getId)
                .noneMatch(id -> id.startsWith("ReactiveCompositeDiscoveryClient_"));
    }

    @Test
    void noRouteMatchesOnServiceIdPathPrefix() {
        assertThat(routes()).allSatisfy(route -> {
            String predicate = route.getPredicate().toString().toLowerCase();
            SERVICE_IDS.forEach(serviceId ->
                    assertThat(predicate).doesNotContain("/" + serviceId + "/"));
        });
    }

    @Test
    void explicitRoutesArePresent() {
        List<String> expected = new ArrayList<>(SERVICE_IDS);
        expected.add(WEBSOCKET_ROUTE_ID);
        assertThat(routes())
                .extracting(Route::getId)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void websocketRouteHasDedupeResponseHeaderDefaultFilter() {
        Route ws = routes().stream()
                .filter(r -> r.getId().equals(WEBSOCKET_ROUTE_ID))
                .findFirst()
                .orElseThrow();
        assertThat(ws.getFilters())
                .anyMatch(f -> f.toString().toLowerCase().contains("deduperesponseheader"));
    }

    @Test
    void mediaRouteServesUserNotifications() {
        Route media = routes().stream()
                .filter(r -> r.getId().equals("media-service"))
                .findFirst()
                .orElseThrow();
        assertThat(media.getPredicate().toString()).contains("/api/v1/notifications/**");
    }
}
