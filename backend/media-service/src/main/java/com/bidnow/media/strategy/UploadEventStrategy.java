/*
 * BidNow Auction System
 */
package com.bidnow.media.strategy;

import com.bidnow.common.enums.MediaEntityType;

import java.util.UUID;

/**
 * Strategy interface for post-upload actions per entity type.
 * Each implementation handles one specific {@link MediaEntityType}
 * and is auto-registered in {@link UploadEventStrategyFactory}.
 *
 * <p>To add support for a new entity type:
 * <ol>
 *   <li>Add the type to {@link MediaEntityType}</li>
 *   <li>Create a new Spring bean implementing this interface</li>
 *   <li>Return the new type from {@link #getSupportedType()}</li>
 * </ol>
 * No changes to {@link UploadEventStrategyFactory} or {@link com.bidnow.media.service.impl.MediaServiceImpl} are needed.
 */
public interface UploadEventStrategy {

    /**
     * The entity type this strategy handles.
     */
    MediaEntityType getSupportedType();

    /**
     * Perform post-upload actions (e.g. publish a domain event).
     *
     * @param ownerId  the user who uploaded the file
     * @param entityId the entity the file is associated with (may be null)
     * @param s3Key    the S3 object key of the uploaded file
     */
    void handle(UUID ownerId, UUID entityId, String s3Key);
}
