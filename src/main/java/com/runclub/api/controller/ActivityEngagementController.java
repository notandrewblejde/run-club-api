package com.runclub.api.controller;

import com.runclub.api.entity.ActivityComment;
import com.runclub.api.service.ActivityEngagementService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/activities/{activityId}")
public class ActivityEngagementController {

    private final ActivityEngagementService engagementService;

    public ActivityEngagementController(ActivityEngagementService engagementService) {
        this.engagementService = engagementService;
    }

    @PostMapping("/kudos")
    public ResponseEntity<?> toggleKudo(@PathVariable UUID activityId, Authentication authentication) {
        try {
            UUID userId = userId(authentication);
            boolean kudoed = engagementService.toggleKudo(activityId, userId);
            return ResponseEntity.ok(Map.of("kudoed", kudoed));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/kudos")
    public ResponseEntity<?> hasKudo(@PathVariable UUID activityId, Authentication authentication) {
        try {
            UUID userId = userId(authentication);
            return ResponseEntity.ok(Map.of("kudoed", engagementService.hasKudo(activityId, userId)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/comments")
    public ResponseEntity<?> listComments(
            @PathVariable UUID activityId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            Page<ActivityComment> comments = engagementService.listComments(activityId, page, limit);
            Map<String, Object> response = new HashMap<>();
            response.put("comments", comments.map(c -> Map.of(
                "id", c.getId(),
                "user_id", c.getUser().getId(),
                "user_name", c.getUser().getDisplayName() == null ? "" : c.getUser().getDisplayName(),
                "user_avatar_url", c.getUser().getProfilePicUrl() == null ? "" : c.getUser().getProfilePicUrl(),
                "content", c.getContent(),
                "created_at", c.getCreatedAt()
            )).getContent());
            response.put("total", comments.getTotalElements());
            response.put("page", comments.getNumber() + 1);
            response.put("limit", limit);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/comments")
    public ResponseEntity<?> addComment(
            @PathVariable UUID activityId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            UUID userId = userId(authentication);
            ActivityComment comment = engagementService.addComment(activityId, userId, request.get("content"));
            Map<String, Object> response = new HashMap<>();
            response.put("id", comment.getId());
            response.put("user_id", comment.getUser().getId());
            response.put("content", comment.getContent());
            response.put("created_at", comment.getCreatedAt());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable UUID activityId,
            @PathVariable UUID commentId,
            Authentication authentication) {
        try {
            UUID userId = userId(authentication);
            engagementService.deleteComment(commentId, userId);
            return ResponseEntity.ok(Map.of("message", "Comment deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private UUID userId(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return UUID.nameUUIDFromBytes(jwt.getClaimAsString("sub").getBytes());
    }
}
