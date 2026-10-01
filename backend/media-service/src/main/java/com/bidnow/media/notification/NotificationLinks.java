package com.bidnow.media.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Frontend links: relative paths for in-app notifications, absolute URLs for emails. */
@Component
public class NotificationLinks {

    public static final String AUCTIONS_PATH = "/auctions";
    public static final String WALLET_PATH = "/wallet";
    public static final String SELLER_AUCTIONS_PATH = "/seller/auctions";

    private final String frontendBaseUrl;

    public NotificationLinks(@Value("${app.frontend.base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    public static String auctionPath(UUID auctionId) {
        return AUCTIONS_PATH + "/" + auctionId;
    }

    public String absolute(String path) {
        return frontendBaseUrl + path;
    }
}
