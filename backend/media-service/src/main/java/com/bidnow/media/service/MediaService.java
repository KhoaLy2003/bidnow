/*
 * BidNow Auction System
 */
package com.bidnow.media.service;

import com.bidnow.common.enums.MediaEntityType;
import com.bidnow.media.dto.response.MediaUploadResponse;
import com.bidnow.media.dto.response.PresignedUrlResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MediaService {

    /**
     * Validates, processes (resize/compress), and uploads the file to S3 via the server.
     *
     * @param file       the uploaded multipart file
     * @param ownerId    the ID of the authenticated user uploading the file
     * @param entityId   optional ID of the domain object this file belongs to
     * @param entityType optional type of the domain object (e.g. {@link MediaEntityType#AUCTION_ITEM})
     * @return metadata of the stored asset
     */
    MediaUploadResponse uploadFile(MultipartFile file, UUID ownerId, UUID entityId, MediaEntityType entityType);

    /**
     * Generates a pre-signed S3 URL for direct client-to-S3 upload.
     * The client uploads the file directly to S3 using this URL.
     *
     * @param fileName    the desired file name
     * @param contentType the MIME type of the file
     * @return the presigned URL and its expiry
     */
    PresignedUrlResponse generatePresignedUrl(String fileName, String contentType);

    /**
     * Returns a pre-signed URL to download/view a file from S3.
     *
     * @param s3Key the S3 object key
     * @return the presigned download URL
     */
    String generateDownloadUrl(String s3Key);
}
