/*
 * BidNow Auction System
 */
package com.bidnow.media.strategy;

import com.bidnow.common.enums.MediaEntityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Auto-discovers all {@link UploadEventStrategy} beans and dispatches
 * post-upload events by entity type.
 *
 * <p>Adding a new entity type only requires:
 * <ol>
 *   <li>A new entry in {@link MediaEntityType}</li>
 *   <li>A new {@code @Component} implementing {@link UploadEventStrategy}</li>
 * </ol>
 * This factory does not need to change.
 */
@Slf4j
@Component
public class UploadEventStrategyFactory {

    private final Map<MediaEntityType, UploadEventStrategy> strategies;

    public UploadEventStrategyFactory(List<UploadEventStrategy> strategyList) {
        strategies = new EnumMap<>(MediaEntityType.class);
        for (UploadEventStrategy strategy : strategyList) {
            strategies.put(strategy.getSupportedType(), strategy);
            log.info("Registered upload strategy: {} → {}", strategy.getSupportedType(), strategy.getClass().getSimpleName());
        }
    }

    /**
     * Dispatch a post-upload action for the given entity type.
     * If no strategy is registered for the type, the call is a no-op with a warning.
     */
    public void dispatch(MediaEntityType entityType, UUID ownerId, UUID entityId, String s3Key, String publicUrl) {
        Optional.ofNullable(strategies.get(entityType))
                .ifPresentOrElse(
                        strategy -> strategy.handle(ownerId, entityId, s3Key, publicUrl),
                        () -> log.warn("No upload event strategy registered for entityType={}", entityType)
                );
    }
}
