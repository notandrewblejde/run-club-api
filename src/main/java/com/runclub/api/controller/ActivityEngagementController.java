package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.dto.CreateCommentRequest;
import com.runclub.api.model.Comment;
import com.runclub.api.model.Kudo;
import com.runclub.api.service.ActivityEngagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/activities/{activityId}")
@Tag(name = "Activity engagement")
public class ActivityEngagementController {

    private final ActivityEngagementService engagementService;

    public ActivityEngagementController(ActivityEngagementService engagementService) {
        this.engagementService = engagementService;
    }

    /** Idempotent toggle: POST same activity again to remove the kudo. */
    @PostMapping("/kudos")
    public Kudo toggleKudo(@PathVariable UUID activityId, Authentication authentication) {
        return engagementService.toggleKudo(activityId, Auth.userId(authentication));
    }

    @GetMapping("/kudos")
    public Kudo getKudo(@PathVariable UUID activityId, Authentication authentication) {
        return engagementService.getKudoState(activityId, Auth.userId(authentication));
    }

    @GetMapping("/comments")
    public ApiList<Comment> listComments(
            @PathVariable UUID activityId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {
        Page<com.runclub.api.entity.ActivityComment> comments = engagementService.listComments(activityId, page, limit);
        List<Comment> data = comments.getContent().stream().map(Comment::from).toList();
        return ApiList.of(data, comments.hasNext(), comments.getTotalElements(),
            "/v1/activities/" + activityId + "/comments");
    }

    @PostMapping("/comments")
    public ResponseEntity<Comment> addComment(
            @PathVariable UUID activityId,
            @Valid @RequestBody CreateCommentRequest body,
            Authentication authentication) {
        Comment created = engagementService.addComment(activityId, Auth.userId(authentication), body.content);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable UUID activityId,
            @PathVariable UUID commentId,
            Authentication authentication) {
        engagementService.deleteComment(commentId, Auth.userId(authentication));
    }
}
