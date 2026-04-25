package com.runclub.api.controller;

import com.runclub.api.entity.Post;
import com.runclub.api.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clubs")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/{clubId}/posts")
    public ResponseEntity<?> createPost(
            @PathVariable UUID clubId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String authId = jwt.getClaimAsString("sub");
            UUID userId = UUID.nameUUIDFromBytes(authId.getBytes());

            String content = (String) request.get("content");
            Object photosObj = request.get("photos");
            String[] photos = null;

            if (photosObj instanceof java.util.List) {
                java.util.List<?> photoList = (java.util.List<?>) photosObj;
                photos = photoList.stream().map(Object::toString).toArray(String[]::new);
            } else if (photosObj instanceof String[]) {
                photos = (String[]) photosObj;
            }

            Post post = postService.createPost(clubId, userId, content, photos);

            Map<String, Object> response = new HashMap<>();
            response.put("id", post.getId());
            response.put("club_id", post.getClub().getId());
            response.put("author_id", post.getAuthor().getId());
            response.put("content", post.getContent());
            response.put("photos", post.getPhotoUrls());
            response.put("created_at", post.getCreatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{clubId}/posts/{postId}")
    public ResponseEntity<?> deletePost(
            @PathVariable UUID clubId,
            @PathVariable UUID postId,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String authId = jwt.getClaimAsString("sub");
            UUID userId = UUID.nameUUIDFromBytes(authId.getBytes());

            postService.deletePost(postId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Post deleted successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{clubId}/feed")
    public ResponseEntity<?> getClubFeed(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Map<String, Object> feed = postService.getClubFeed(clubId, page, limit);
            return ResponseEntity.ok(feed);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
