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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "Users")
public class UserController {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final ActivityService activityService;
    private final FollowService followService;
    private final AvatarUploadService avatarUploadService;

    public UserController(UserRepository userRepository,
                          UserProfileService userProfileService,
                          ActivityService activityService,
                          FollowService followService,
                          AvatarUploadService avatarUploadService) {
        this.userRepository = userRepository;
        this.userProfileService = userProfileService;
        this.activityService = activityService;
        this.followService = followService;
        this.avatarUploadService = avatarUploadService;
    }

    @GetMapping("/me")
    public UserProfile getMe(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String auth0Id = jwt.getClaimAsString("sub");
        User user = userRepository.findByAuth0Id(auth0Id).orElseGet(() -> {
            User u = new User();
            u.setAuth0Id(auth0Id);
            u.setEmail(Optional.ofNullable(jwt.getClaimAsString("email")).orElse(auth0Id + "@unknown"));
            u.setDisplayName(jwt.getClaimAsString("name"));
            u.setProfilePicUrl(jwt.getClaimAsString("picture"));
            u.setId(UUID.nameUUIDFromBytes(auth0Id.getBytes()));
            return userRepository.save(u);
        });
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
            @RequestParam(defaultValue = "20") int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            return ApiList.of(List.of(), false, 0, "/v1/users");
        }
        int cap = Math.min(limit, 50);
        List<com.runclub.api.model.User> results = userRepository.findAll().stream()
            .filter(u -> {
                String name = u.getDisplayName() == null ? "" : u.getDisplayName().toLowerCase();
                String email = u.getEmail() == null ? "" : u.getEmail().toLowerCase();
                return name.contains(q) || email.contains(q);
            })
            .limit(cap)
            .map(com.runclub.api.model.User::from)
            .toList();
        return ApiList.of(results, false, results.size(), "/v1/users");
    }
}
