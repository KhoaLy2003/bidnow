/*
 * BidNow Auction System
 */
package com.bidnow.common.enums;

/**
 * Classifies the entity a media asset belongs to.
 * Both media-service (producer) and consumer services use this enum
 * to avoid hardcoded strings across the codebase.
 */
public enum MediaEntityType {
    USER_AVATAR,
    AUCTION_ITEM,
    // Add new types here as the project grows
}
