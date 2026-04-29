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
 * Issues presigned S3 PUT URLs for club cover photo uploads.
 * Same pattern as AvatarUploadService — client uploads direct to S3,
 * then PATCHes /v1/clubs/{id} with the resulting cover_image_url.
 */
@Service
public class ClubUploadService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Duration TTL = Duration.ofMinutes(5);

    @Value("${s3.bucket:}")
    private String bucket;

    @Value("${aws.region:us-east-1}")
    private String region;

    private final S3Presigner presigner;

    public ClubUploadService(S3Presigner presigner) {
        this.presigner = presigner;
    }

    public Map<String, String> presignCoverUpload(UUID clubId, String contentType) {
        if (bucket == null || bucket.isBlank()) throw ApiException.badRequest("S3 bucket not configured");
        String mime = contentType == null ? "image/jpeg" : contentType.toLowerCase();
        if (!ALLOWED.contains(mime)) throw ApiException.badRequest("Unsupported content type: " + mime);

        String ext = mime.substring(mime.indexOf('/') + 1);
        String key = String.format("clubs/%s/cover/%s.%s", clubId, UUID.randomUUID(), ext);

        var objectRequest = PutObjectRequest.builder().bucket(bucket).key(key).contentType(mime).build();
        var presignRequest = PutObjectPresignRequest.builder().signatureDuration(TTL).putObjectRequest(objectRequest).build();
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
