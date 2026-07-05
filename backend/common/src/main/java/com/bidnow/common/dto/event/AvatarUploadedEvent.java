/*
 * BidNow Auction System
 */
package com.bidnow.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarUploadedEvent {
    /**
     * The user whose avatar was updated.
     */
    private UUID userId;
    /**
     * The S3 object key of the uploaded image.
     */
    private String s3Key;
    /**
     * Supabase public URL for the image. Stored as avatar_url in user_profiles.
     */
    private String publicUrl;
}
