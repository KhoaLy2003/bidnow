package com.bidnow.media.projection;

import com.bidnow.media.repository.AuctionProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read side of media's auction projection, as notification handlers need it. */
@Component
@RequiredArgsConstructor
public class AuctionLookup {

    public static final String UNKNOWN_TITLE = "your auction";

    private final AuctionProjectionRepository repository;

    /** The event's title when present, else the projected title, else {@link #UNKNOWN_TITLE}. */
    public String title(UUID auctionId, String eventTitle) {
        if (StringUtils.hasText(eventTitle)) {
            return eventTitle;
        }
        return repository.findAuction(auctionId)
                .map(AuctionRef::title)
                .filter(StringUtils::hasText)
                .orElse(UNKNOWN_TITLE);
    }

    public Optional<UUID> sellerId(UUID auctionId) {
        return repository.findAuction(auctionId).map(AuctionRef::sellerId);
    }

    /** Everyone who has bid on the auction (from bid-placed events). */
    public List<UUID> participants(UUID auctionId) {
        return repository.findParticipantIds(auctionId);
    }
}
