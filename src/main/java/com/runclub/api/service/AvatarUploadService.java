package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Issues short-lived S3 presigned PUT URLs so the mobile client can upload
 * an avatar directly to S3 without streaming through our backend. The mobile
 * app then PATCHes /v1/users/me with the resulting public_url.
 */
@Service
public class AvatarUploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Duration URL_TTL = Duration.ofMinutes(5);

    @Value("${s3.bucket:}")
    private String bucket;

    @Value("${aws.region:us-east-1}")
    private String region;

    private final S3Presigner presigner;

    public AvatarUploadService(S3Presigner presigner) {
        this.presigner = presigner;
    }

    public Map<String, String> presignAvatarUpload(UUID userId, String contentType) {
        if (bucket == null || bucket.isBlank()) {
            throw ApiException.badRequest("S3 bucket not configured");
        }
        String mime = contentType == null ? "image/jpeg" : contentType.toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(mime)) {
            throw ApiException.badRequest("Unsupported content type: " + mime);
        }

        String ext = mime.substring(mime.indexOf('/') + 1);
        // Path includes the user id (so multiple uploads don't collide between users)
        // and a UUID (so a stale CDN cache doesn't keep showing the old image).
        String key = String.format("avatars/%s/%s.%s", userId, UUID.randomUUID(), ext);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(mime)
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(URL_TTL)
            .putObjectRequest(objectRequest)
            .build();

        var presigned = presigner.presignPutObject(presignRequest);

        String publicUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
        return Map.of(
            "upload_url", presigned.url().toString(),
            "public_url", publicUrl,
            "method", "PUT",
            "content_type", mime
        );
    }
}
