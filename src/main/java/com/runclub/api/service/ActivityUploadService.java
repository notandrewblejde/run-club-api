package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserRepository;
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
 * Presigned S3 uploads for app-owned activity photos (not Strava URLs).
 */
@Service
public class ActivityUploadService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Duration URL_TTL = Duration.ofMinutes(5);

    @Value("${s3.bucket:}")
    private String bucket;

    @Value("${aws.region:us-east-1}")
    private String region;

    private final S3Presigner presigner;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public ActivityUploadService(S3Presigner presigner,
                                 ActivityRepository activityRepository,
                                 UserRepository userRepository) {
        this.presigner = presigner;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
    }

    public Map<String, String> presignActivityPhotoUpload(UUID activityId, UUID userId, String contentType) {
        if (bucket == null || bucket.isBlank()) {
            throw ApiException.badRequest("S3 bucket not configured");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("user"));
        Activity activity = activityRepository.findById(activityId).orElseThrow(() -> ApiException.notFound("activity"));
        if (!activity.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("Only the activity owner can upload photos");
        }

        String mime = contentType == null ? "image/jpeg" : contentType.toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(mime)) {
            throw ApiException.badRequest("Unsupported content type: " + mime);
        }
        String ext = mime.substring(mime.indexOf('/') + 1);
        String key = String.format("activities/%s/user/%s/%s.%s", activityId, userId, UUID.randomUUID(), ext);

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
