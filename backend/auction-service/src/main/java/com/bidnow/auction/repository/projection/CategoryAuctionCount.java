package com.bidnow.auction.repository.projection;

import java.util.UUID;

public interface CategoryAuctionCount {
    UUID getCategoryId();
    String getCategoryName();
    String getSlug();
    Long getCount();
}
