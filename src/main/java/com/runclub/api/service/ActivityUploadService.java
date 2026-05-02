package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
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

    /** How long a read presigned URL stays valid. 7 days is long enough to survive cache without being a security risk. */
    private static final Duration READ_URL_TTL = Duration.ofDays(7);

    /**
     * Re-sign a list of stored S3 keys/URLs for reading.
     * Stored values may be raw S3 URLs (old) or s3-key paths (new).
     * Returns presigned GET URLs valid for READ_URL_TTL.
     */
    public List<String> presignReadUrls(List<String> storedUrls) {
        if (storedUrls == null || storedUrls.isEmpty()) return List.of();
        return storedUrls.stream().map(url -> {
            String key = extractKey(url);
            GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(key).build();
            GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(READ_URL_TTL)
                .getObjectRequest(getReq)
                .build();
            return presigner.presignGetObject(presignReq).url().toString();
        }).toList();
    }

    /** Extract S3 key from a full S3 URL or return as-is if already a key. */
    private String extractKey(String urlOrKey) {
        // Handle https://bucket.s3.region.amazonaws.com/key or https://bucket.s3.amazonaws.com/key
        if (urlOrKey.startsWith("https://")) {
            int pathStart = urlOrKey.indexOf('/', 8); // skip "https://"
            if (pathStart >= 0) {
                String path = urlOrKey.substring(pathStart + 1);
                // Strip query string if present (old presigned URL)
                int q = path.indexOf('?');
                return q >= 0 ? path.substring(0, q) : path;
            }
        }
        return urlOrKey;
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

        // Return a presigned GET URL so the client can display the photo immediately after upload.
        // We also return the raw S3 key so the API can store it and re-sign on future reads.
        GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(key).build();
        GetObjectPresignRequest getPresignReq = GetObjectPresignRequest.builder()
            .signatureDuration(READ_URL_TTL)
            .getObjectRequest(getReq)
            .build();
        String readUrl = presigner.presignGetObject(getPresignReq).url().toString();

        return Map.of(
            "upload_url", presigned.url().toString(),
            "public_url", readUrl,   // presigned GET URL — valid for 7 days
            "s3_key", key,           // raw key for re-signing on future loads
            "method", "PUT",
            "content_type", mime
        );
    }
}
