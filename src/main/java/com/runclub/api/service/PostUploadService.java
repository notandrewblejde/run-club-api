package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Club;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ClubMembershipRepository;
import com.runclub.api.repository.ClubRepository;
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
 * Presigned S3 upload URLs for club post photos.
 */
@Service
public class PostUploadService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Duration URL_TTL = Duration.ofMinutes(5);

    @Value("${s3.bucket:}")
    private String bucket;

    @Value("${aws.region:us-east-1}")
    private String region;

    private final S3Presigner presigner;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final ClubMembershipRepository clubMembershipRepository;

    public PostUploadService(S3Presigner presigner,
                             ClubRepository clubRepository,
                             UserRepository userRepository,
                             ClubMembershipRepository clubMembershipRepository) {
        this.presigner = presigner;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.clubMembershipRepository = clubMembershipRepository;
    }

    public Map<String, String> presignPostPhotoUpload(UUID clubId, UUID userId, String contentType) {
        if (bucket == null || bucket.isBlank()) {
            throw ApiException.badRequest("S3 bucket not configured");
        }
        Club club = clubRepository.findById(clubId).orElseThrow(() -> ApiException.notFound("club"));
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("user"));
        clubMembershipRepository.findByClubAndUser(club, user)
            .orElseThrow(() -> ApiException.forbidden("User is not a member of this club"));

        String mime = contentType == null ? "image/jpeg" : contentType.toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(mime)) {
            throw ApiException.badRequest("Unsupported content type: " + mime);
        }
        String ext = mime.substring(mime.indexOf('/') + 1);
        String key = String.format("clubs/%s/posts/%s/%s.%s", clubId, userId, UUID.randomUUID(), ext);

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
