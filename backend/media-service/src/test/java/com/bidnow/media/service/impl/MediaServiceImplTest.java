package com.bidnow.media.service.impl;

import com.bidnow.media.domain.entity.MediaAsset;
import com.bidnow.media.dto.response.MediaUploadResponse;
import com.bidnow.media.dto.response.PresignedUrlResponse;
import com.bidnow.media.repository.MediaAssetRepository;
import com.bidnow.media.strategy.UploadEventStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    private static final String CDN = "https://d1234abcd.cloudfront.net";

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private UploadEventStrategyFactory uploadEventStrategyFactory;

    private MediaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MediaServiceImpl(s3Client, s3Presigner, mediaAssetRepository, uploadEventStrategyFactory);
        ReflectionTestUtils.setField(service, "bucketName", "bidnow-media-dev");
        ReflectionTestUtils.setField(service, "cdnBaseUrl", CDN);
    }

    @Test
    void buildPublicUrl_joinsCdnBaseAndKey() {
        assertThat(service.buildPublicUrl("uploads/a/b.jpg")).isEqualTo(CDN + "/uploads/a/b.jpg");
    }

    @Test
    void buildPublicUrl_ignoresTrailingSlashOnBase() {
        ReflectionTestUtils.setField(service, "cdnBaseUrl", CDN + "/");

        assertThat(service.buildPublicUrl("uploads/a/b.jpg")).isEqualTo(CDN + "/uploads/a/b.jpg");
    }

    @Test
    void uploadFile_returnsCdnUrlAndSetsImmutableCacheControl() throws Exception {
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID ownerId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes());

        MediaUploadResponse response = service.uploadFile(file, ownerId, null, null);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest put = captor.getValue();
        assertThat(put.bucket()).isEqualTo("bidnow-media-dev");
        assertThat(put.key()).startsWith("uploads/" + ownerId + "/").endsWith(".png");
        assertThat(put.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(response.getPublicUrl()).isEqualTo(CDN + "/" + put.key());
    }

    @Test
    void generatePresignedUrl_returnsCdnPublicUrlForTheKey() throws Exception {
        PresignedPutObjectRequest presigned = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://bidnow-media-dev.s3.amazonaws.com/signed"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        PresignedUrlResponse response = service.generatePresignedUrl("photo.jpg", "image/jpeg");

        assertThat(response.getS3Key()).startsWith("uploads/").endsWith(".jpg");
        assertThat(response.getPublicUrl()).isEqualTo(CDN + "/" + response.getS3Key());
        assertThat(response.getUploadUrl()).isEqualTo("https://bidnow-media-dev.s3.amazonaws.com/signed");
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
