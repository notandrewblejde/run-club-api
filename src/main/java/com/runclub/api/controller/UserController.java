package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.dto.UpdateUserRequest;
import com.runclub.api.entity.User;
import com.runclub.api.model.Activity;
import com.runclub.api.model.UserProfile;
import com.runclub.api.repository.UserRepository;
import com.runclub.api.service.ActivityService;
import com.runclub.api.service.AvatarUploadService;
import com.runclub.api.service.FollowService;
import com.runclub.api.service.UserProfileService;
import com.runclub.api.service.StravaActivitySyncService;
import com.runclub.api.service.UserProvisioningService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "Users")
public class UserController {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final ActivityService activityService;
    private final FollowService followService;
    private final StravaActivitySyncService stravaActivitySyncService;
    private final AvatarUploadService avatarUploadService;
    private final UserProvisioningService userProvisioningService;

    public UserController(UserRepository userRepository,
                          UserProfileService userProfileService,
                          ActivityService activityService,
                          FollowService followService,
                          AvatarUploadService avatarUploadService,
                          UserProvisioningService userProvisioningService) {
        this.userRepository = userRepository;
        this.userProfileService = userProfileService;
        this.activityService = activityService;
        this.followService = followService;
        this.avatarUploadService = avatarUploadService;
        this.stravaActivitySyncService = stravaActivitySyncService;
        this.userProvisioningService = userProvisioningService;
    }

    @GetMapping("/me")
    public UserProfile getMe(Authentication authentication) {
        // The UserProvisioningFilter has already JIT-created this row for any
        // authenticated request, so this is a guaranteed-present lookup. We
        // still call the service rather than findByAuth0Id directly to keep
        // a single source of truth for provisioning semantics.
        Jwt jwt = (Jwt) authentication.getPrincipal();
        User user = userProvisioningService.getOrProvision(jwt);
        return userProfileService.getProfile(user.getId(), user.getId());
    }

    @GetMapping("/{userId}")
    public UserProfile getById(@PathVariable UUID userId, Authentication authentication) {
        return userProfileService.getProfile(userId, Auth.userId(authentication));
    }

    /**
     * Activities for a specific user. Visibility:
     *   - own activities: always
     *   - public profile: anyone authenticated
     *   - private profile: accepted followers only
     */
    @GetMapping("/{userId}/activities")
    public ApiList<Activity> listUserActivities(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        UUID viewerId = Auth.userId(authentication);
        User owner = userRepository.findById(userId)
            .orElseThrow(() -> com.runclub.api.api.ApiException.notFound("user"));
        boolean isOwner = viewerId.equals(userId);
        boolean isPrivate = "private".equals(owner.getPrivacyLevel());
        if (isPrivate && !isOwner && !followService.isAcceptedFollower(viewerId, userId)) {
            throw com.runclub.api.api.ApiException.forbidden("Activities are private");
        }
        Page<com.runclub.api.entity.Activity> activities = activityService.getUserActivities(userId, page, limit);
        List<Activity> data = activities.getContent().stream().map(Activity::from).toList();
        return ApiList.of(data, activities.hasNext(), activities.getTotalElements(),
            "/v1/users/" + userId + "/activities");
    }

    @PatchMapping("/me")
    public UserProfile updateMe(
            @Valid @RequestBody UpdateUserRequest body,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String auth0Id = jwt.getClaimAsString("sub");
        User user = userRepository.findByAuth0Id(auth0Id)
            .orElseThrow(() -> com.runclub.api.api.ApiException.notFound("user"));

        if (body.name != null) user.setDisplayName(body.name);
        if (body.bio != null) user.setBio(body.bio);
        if (body.city != null) user.setCity(body.city);
        if (body.state != null) user.setState(body.state);
        if (body.avatarUrl != null) user.setProfilePicUrl(body.avatarUrl);
        if (body.privacyLevel != null) user.setPrivacyLevel(body.privacyLevel);
        userRepository.save(user);
        return userProfileService.getProfile(user.getId(), user.getId());
    }

    @PostMapping("/me/avatar/presign")
    public Map<String, String> presignAvatarUpload(
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        String contentType = body == null ? null : body.get("content_type");
        return avatarUploadService.presignAvatarUpload(userId, contentType);
    }

    @GetMapping
    public ApiList<com.runclub.api.model.User> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return ApiList.of(List.of(), false, 0, "/v1/users");
        }
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 50));
        Page<User> hits = userRepository.searchByDisplayNamePrefix(q, pageable);
        List<com.runclub.api.model.User> data = hits.getContent().stream()
            .map(com.runclub.api.model.User::from)
            .toList();
        return ApiList.of(data, hits.hasNext(), hits.getTotalElements(), "/v1/users");
    }

    /**
     * "People you may know" for the authenticated viewer. Friends-of-friends
     * with a recent-public-users fallback for brand-new accounts. See
     * {@link com.runclub.api.service.FollowService#getSuggestedUsers}.
     */
    @GetMapping("/suggested")
    public ApiList<com.runclub.api.model.User> suggested(Authentication authentication) {
        UUID viewerId = Auth.userId(authentication);
        List<com.runclub.api.model.User> data = followService.getSuggestedUsers(viewerId).stream()
            .map(com.runclub.api.model.User::from)
            .toList();
        return ApiList.of(data, false, data.size(), "/v1/users/suggested");
    }

    @PostMapping("/me/strava/deep-sync")
    public ResponseEntity<?> deepSync(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        stravaActivitySyncService.deepSyncRecentActivitiesAsync(userId);
        return ResponseEntity.accepted().body("{\"message\": \"Deep sync started — GPS data will populate shortly\"}");
    }
}