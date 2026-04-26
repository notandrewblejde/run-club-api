package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.dto.UpdateActivityRequest;
import com.runclub.api.model.Activity;
import com.runclub.api.api.ApiException;
import com.runclub.api.service.ActivityService;
import com.runclub.api.service.ActivityUploadService;
import com.runclub.api.service.AthleteIntelligenceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/activities")
@Tag(name = "Activities")
public class ActivityController {

    private final ActivityService activityService;
    private final AthleteIntelligenceService athleteIntelligenceService;
    private final ActivityUploadService activityUploadService;

    public ActivityController(ActivityService activityService,
                              AthleteIntelligenceService athleteIntelligenceService,
                              ActivityUploadService activityUploadService) {
        this.activityService = activityService;
        this.athleteIntelligenceService = athleteIntelligenceService;
        this.activityUploadService = activityUploadService;
    }

    @GetMapping
    public ApiList<Activity> listMyActivities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        Page<com.runclub.api.entity.Activity> activities = activityService.getUserActivities(userId, page, limit);
        List<Activity> data = activities.getContent().stream().map(Activity::from).toList();
        return ApiList.of(data, activities.hasNext(), activities.getTotalElements(), "/v1/activities");
    }

    @GetMapping("/{activityId}")
    public Activity getActivity(@PathVariable UUID activityId, Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        return activityService.getActivity(activityId, userId);
    }

    @PatchMapping("/{activityId}")
    public Activity patchActivity(
            @PathVariable UUID activityId,
            @Valid @RequestBody UpdateActivityRequest body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        return activityService.updateActivityDetails(activityId, userId, body);
    }

    @PostMapping("/{activityId}/photos/presign")
    public Map<String, String> presignActivityPhoto(
            @PathVariable UUID activityId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String contentType = body == null ? null : body.get("content_type");
        return activityUploadService.presignActivityPhotoUpload(activityId, Auth.userId(authentication), contentType);
    }

    @GetMapping("/{activityId}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable UUID activityId,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        // Authorization piggybacks on getActivity (throws 404/403 if needed).
        activityService.getActivity(activityId, userId);
        String summary = athleteIntelligenceService.getOrCreateActivitySummary(activityId);
        return ResponseEntity.ok(Map.of("activity_id", activityId, "summary", summary));
    }

    @PostMapping("/{activityId}/coach/chat")
    public ResponseEntity<Map<String, String>> coachChat(
            @PathVariable UUID activityId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        activityService.getActivity(activityId, userId);
        String message = body == null ? null : body.get("message");
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("message is required");
        }
        String reply = athleteIntelligenceService.coachChatAboutActivity(activityId, userId, message.trim());
        return ResponseEntity.ok(Map.of("reply", reply));
    }
}
