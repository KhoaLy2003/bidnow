/*
 * BidNow Auction System
 */
package com.bidnow.media.service.impl;

import com.bidnow.common.annotation.Loggable;
import com.bidnow.common.enums.MediaEntityType;
import com.bidnow.media.domain.entity.MediaAsset;
import com.bidnow.media.dto.response.MediaUploadResponse;
import com.bidnow.media.dto.response.PresignedUrlResponse;
import com.bidnow.media.repository.MediaAssetRepository;
import com.bidnow.media.service.MediaService;
import com.bidnow.media.strategy.UploadEventStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Loggable(logResult = false)
public class MediaServiceImpl implements MediaService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int MAX_DIMENSION = 1920;
    private static final Duration PRESIGNED_DURATION = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MediaAssetRepository mediaAssetRepository;
    private final UploadEventStrategyFactory uploadEventStrategyFactory;

    @Value("${aws.s3.BUCKET_NAME}")
    private String bucketName;

    @Value("${supabase.public-url}")
    private String supabasePublicUrl;

    private String buildPublicUrl(String s3Key) {
        String base = supabasePublicUrl.endsWith("/") ? supabasePublicUrl.substring(0, supabasePublicUrl.length() - 1) : supabasePublicUrl;
        return base + "/storage/v1/object/public/" + bucketName + "/" + s3Key;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Server-side upload
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public MediaUploadResponse uploadFile(MultipartFile file, UUID ownerId, UUID entityId, MediaEntityType entityType) {
        validateFile(file);

        try {
            byte[] processedBytes = processImage(file);
            BufferedImage processedImage = ImageIO.read(new ByteArrayInputStream(processedBytes));

            String extension = getExtension(file.getOriginalFilename());
            String s3Key = buildS3Key(ownerId, extension);

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength((long) processedBytes.length)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(processedBytes));
            log.info("Uploaded file to S3: {}", s3Key);

            MediaAsset asset = MediaAsset.builder()
                    .ownerId(ownerId)
                    .entityId(entityId)
                    .entityType(entityType != null ? entityType.name() : null)
                    .originalName(file.getOriginalFilename())
                    .s3Key(s3Key)
                    .contentType(file.getContentType())
                    .fileSize((long) processedBytes.length)
                    .width(processedImage != null ? processedImage.getWidth() : null)
                    .height(processedImage != null ? processedImage.getHeight() : null)
                    .isProcessed(true)
                    .build();

            MediaAsset saved = mediaAssetRepository.save(asset);

            String publicUrl = buildPublicUrl(s3Key);

            // Dispatch post-upload event via Strategy Pattern — no if-blocks needed
            if (entityType != null && ownerId != null) {
                uploadEventStrategyFactory.dispatch(entityType, ownerId, entityId, s3Key, publicUrl);
            }

            return MediaUploadResponse.builder()
                    .id(saved.getId())
                    .originalName(saved.getOriginalName())
                    .s3Key(saved.getS3Key())
                    .publicUrl(publicUrl)
                    .contentType(saved.getContentType())
                    .fileSize(saved.getFileSize())
                    .width(saved.getWidth())
                    .height(saved.getHeight())
                    .createdAt(saved.getCreatedAt())
                    .build();

        } catch (IOException e) {
            log.error("Failed to process or upload file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Presigned upload URL
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public PresignedUrlResponse generatePresignedUrl(String fileName, String contentType) {
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }

        String extension = getExtension(fileName);
        String s3Key = "uploads/" + UUID.randomUUID() + "." + extension;
        Instant expiresAt = Instant.now().plus(PRESIGNED_DURATION);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_DURATION)
                .putObjectRequest(r -> r.bucket(bucketName).key(s3Key).contentType(contentType))
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return PresignedUrlResponse.builder()
                .uploadUrl(presignedRequest.url().toString())
                .s3Key(s3Key)
                .publicUrl(buildPublicUrl(s3Key))
                .expiresAt(expiresAt)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Unsupported file type: " + file.getContentType()
                    + ". Allowed types: " + ALLOWED_TYPES);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the 10 MB limit");
        }
    }

    private byte[] processImage(MultipartFile file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String formatName = getExtension(file.getOriginalFilename());

        Thumbnails.of(file.getInputStream())
                .size(MAX_DIMENSION, MAX_DIMENSION)
                .keepAspectRatio(true)
                .outputFormat(formatName.equals("jpg") ? "jpeg" : formatName)
                .toOutputStream(outputStream);

        return outputStream.toByteArray();
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "jpg";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String buildS3Key(UUID ownerId, String extension) {
        return "uploads/" + ownerId + "/" + UUID.randomUUID() + "." + extension;
    }
}
