package com.runclub.api.controller;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.Follow;
import com.runclub.api.service.FollowService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/users/{userId}/follow")
    public ResponseEntity<?> followUser(
            @PathVariable UUID userId,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String authId = jwt.getClaimAsString("sub");
            UUID followerId = UUID.nameUUIDFromBytes(authId.getBytes());

            Follow follow = followService.followUser(followerId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("follower_id", follow.getFollower().getId());
            response.put("following_id", follow.getFollowing().getId());
            response.put("created_at", follow.getCreatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/users/{userId}/unfollow")
    public ResponseEntity<?> unfollowUser(
            @PathVariable UUID userId,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String authId = jwt.getClaimAsString("sub");
            UUID followerId = UUID.nameUUIDFromBytes(authId.getBytes());

            followService.unfollowUser(followerId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Unfollowed successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users/{userId}/followers")
    public ResponseEntity<?> getFollowers(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Page<Follow> followers = followService.getFollowers(userId, page, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("followers", followers.map(f -> Map.of(
                "user_id", f.getFollower().getId(),
                "name", f.getFollower().getDisplayName(),
                "avatar_url", f.getFollower().getProfilePicUrl(),
                "followed_at", f.getCreatedAt()
            )).getContent());
            response.put("total", followers.getTotalElements());
            response.put("page", followers.getNumber() + 1);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users/{userId}/following")
    public ResponseEntity<?> getFollowing(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Page<Follow> following = followService.getFollowing(userId, page, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("following", following.map(f -> Map.of(
                "user_id", f.getFollowing().getId(),
                "name", f.getFollowing().getDisplayName(),
                "avatar_url", f.getFollowing().getProfilePicUrl(),
                "followed_at", f.getCreatedAt()
            )).getContent());
            response.put("total", following.getTotalElements());
            response.put("page", following.getNumber() + 1);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/feed/home")
    public ResponseEntity<?> getHomeFeed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String authId = jwt.getClaimAsString("sub");
            UUID userId = UUID.nameUUIDFromBytes(authId.getBytes());

            Page<Activity> feed = followService.getHomeFeed(userId, page, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("activities", feed.getContent());
            response.put("total", feed.getTotalElements());
            response.put("page", feed.getNumber() + 1);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
