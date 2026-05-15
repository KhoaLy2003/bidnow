/*
 * BidNow Auction System
 */
package com.bidnow.media.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.enums.MediaEntityType;
import com.bidnow.media.dto.response.MediaUploadResponse;
import com.bidnow.media.dto.response.PresignedUrlResponse;
import com.bidnow.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "APIs for file upload, download, and presigned URL generation")
public class MediaController {

    private final MediaService mediaService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/media/upload
    // Server-side: validate → resize → upload to S3 → save metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Upload a file (server-side)",
            description = "The file passes through the media-service for validation, resizing, and then is uploaded to S3. " +
                    "Allowed types: image/jpeg, image/png, image/webp. Max size: 10 MB."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "File uploaded successfully",
                    content = @Content(schema = @Schema(implementation = MediaUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file type or size"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<MediaUploadResponse>> uploadFile(
            @Parameter(description = "Image file to upload", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Authenticated user ID (injected by gateway)", hidden = true)
            @AuthenticatedUserId UUID ownerId,
            @Parameter(description = "Optional: ID of the entity this file belongs to (e.g. auctionId)")
            @RequestParam(required = false) UUID entityId,
            @Parameter(description = "Optional: Type of the entity (e.g. USER_AVATAR, AUCTION_ITEM)")
            @RequestParam(required = false) MediaEntityType entityType
    ) {
        log.info("Received server-side upload request from user={}, entityId={}, entityType={}",
                ownerId, entityId, entityType);

        MediaUploadResponse response = mediaService.uploadFile(file, ownerId, entityId, entityType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("File uploaded successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/media/presigned-url
    // Generate a presigned PUT URL → client uploads directly to S3
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Generate a presigned upload URL (client-side direct upload)",
            description = "Returns a short-lived S3 presigned PUT URL. The client uploads the file directly to S3 " +
                    "using this URL — the file never passes through the media-service server. " +
                    "URL is valid for 15 minutes."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Presigned URL generated",
                    content = @Content(schema = @Schema(implementation = PresignedUrlResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unsupported content type"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/presigned-url")
    public ResponseEntity<BaseResponse<PresignedUrlResponse>> getPresignedUploadUrl(
            @Parameter(description = "Original file name (used to derive the S3 extension)", required = true)
            @RequestParam String fileName,
            @Parameter(description = "MIME type of the file, e.g. image/png", required = true)
            @RequestParam String contentType
    ) {
        log.info("Generating presigned upload URL for fileName={}, contentType={}", fileName, contentType);

        PresignedUrlResponse response = mediaService.generatePresignedUrl(fileName, contentType);
        return ResponseEntity.ok(BaseResponse.success("Presigned URL generated", response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/media/download
    // Generate a presigned GET URL for the given S3 key
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Get a presigned download URL",
            description = "Returns a presigned S3 GET URL (valid 1 hour) for the given S3 key. " +
                    "Use the s3Key returned from the upload endpoint."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Download URL generated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/download")
    public ResponseEntity<BaseResponse<String>> getDownloadUrl(
            @Parameter(description = "S3 object key returned from upload", required = true)
            @RequestParam String s3Key
    ) {
        log.info("Generating presigned download URL for s3Key={}", s3Key);

        String downloadUrl = mediaService.generateDownloadUrl(s3Key);
        return ResponseEntity.ok(BaseResponse.success("Download URL generated", downloadUrl));
    }
}
