package com.runclub.api.controller;

import com.runclub.api.api.Auth;
import com.runclub.api.dto.CreatePostRequest;
import com.runclub.api.dto.UpdatePostRequest;
import com.runclub.api.model.Post;
import com.runclub.api.service.PostService;
import com.runclub.api.service.PostUploadService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clubs/{clubId}")
@Tag(name = "Club posts")
public class PostController {

    private final PostService postService;
    private final PostUploadService postUploadService;

    public PostController(PostService postService, PostUploadService postUploadService) {
        this.postService = postService;
        this.postUploadService = postUploadService;
    }

    @PostMapping("/posts")
    public ResponseEntity<Post> createPost(
            @PathVariable UUID clubId,
            @Valid @RequestBody CreatePostRequest body,
            Authentication authentication) {
        com.runclub.api.entity.Post saved = postService.createPost(
            clubId, Auth.userId(authentication), body.content, body.photos);
        return ResponseEntity.status(HttpStatus.CREATED).body(Post.from(saved));
    }

    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @PathVariable UUID clubId,
            @PathVariable UUID postId,
            Authentication authentication) {
        postService.deletePost(clubId, postId, Auth.userId(authentication));
    }

    @GetMapping("/posts/{postId}")
    public Post getPost(
            @PathVariable UUID clubId,
            @PathVariable UUID postId) {
        return Post.from(postService.getPost(clubId, postId));
    }

    @PatchMapping("/posts/{postId}")
    public Post updatePost(
            @PathVariable UUID clubId,
            @PathVariable UUID postId,
            @Valid @RequestBody UpdatePostRequest body,
            Authentication authentication) {
        com.runclub.api.entity.Post saved = postService.updatePost(
            clubId, postId, Auth.userId(authentication), body.content, body.photos);
        return Post.from(saved);
    }

    @PostMapping("/posts/presign")
    public Map<String, String> presignPostPhotoUpload(
            @PathVariable UUID clubId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String contentType = body == null ? null : body.get("content_type");
        return postUploadService.presignPostPhotoUpload(clubId, Auth.userId(authentication), contentType);
    }

    /**
     * Mixed feed of posts + activities, kept loose-typed for now.
     * Could be promoted to a typed FeedItem with a discriminated union later.
     */
    @GetMapping("/feed")
    public Map<String, Object> getClubFeed(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return postService.getClubFeed(clubId, page, limit);
    }
}
