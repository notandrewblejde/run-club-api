package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.dto.UpdateActivityRequest;
import com.runclub.api.model.Activity;
import com.runclub.api.api.ApiException;
import com.runclub.api.service.ActivityService;
import com.runclub.api.service.ActivityUploadService;
import com.runclub.api.service.AthleteIntelligenceService;
import com.runclub.api.service.TrainingGoalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.runclub.api.model.JsonDtos.ActivitySummary;
import com.runclub.api.model.JsonDtos.CoachReply;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/activities")
@Tag(name = "Activities")
public class ActivityController {

    private static final Logger log = LoggerFactory.getLogger(ActivityController.class);

    private final ActivityService activityService;
    private final AthleteIntelligenceService athleteIntelligenceService;
    private final ActivityUploadService activityUploadService;
    private final TrainingGoalService trainingGoalService;

    public ActivityController(ActivityService activityService,
                              AthleteIntelligenceService athleteIntelligenceService,
                              ActivityUploadService activityUploadService,
                              TrainingGoalService trainingGoalService) {
        this.activityService = activityService;
        this.athleteIntelligenceService = athleteIntelligenceService;
        this.activityUploadService = activityUploadService;
        this.trainingGoalService = trainingGoalService;
    }

    @GetMapping
    public ApiList<Activity> listMyActivities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        Page<com.runclub.api.entity.Activity> activities = activityService.getUserActivities(userId, page, limit);
        List<Activity> data = activities.getContent().stream().map((com.runclub.api.entity.Activity entity) -> {
            Activity dto = Activity.from(entity);
            dto.ownedByViewer = true;
            return dto;
        }).toList();
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
    public ResponseEntity<ActivitySummary> getSummary(
            @PathVariable UUID activityId,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        // Authorization piggybacks on getActivity (throws 404/403 if needed).
        activityService.getActivity(activityId, userId);
        String summary = athleteIntelligenceService.getOrCreateActivitySummary(activityId);
        return ResponseEntity.ok(ActivitySummary.builder()
            .activityId(activityId)
            .summary(summary)
            .build());
    }

    @PostMapping("/{activityId}/coach/chat")
    public ResponseEntity<CoachReply> coachChat(
            @PathVariable UUID activityId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        activityService.getActivity(activityId, userId);
        String message = body == null ? null : body.get("message");
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("message is required");
        }
        String goalContext = null;
        try {
            goalContext = trainingGoalService.buildActivityCoachContextForPrompt(userId);
        } catch (Exception e) {
            // DB migration missing, repo errors, etc. — coach chat still works without goal context
            log.warn("Skipping training-goal context for activity coach chat: {}", e.toString());
        }
        String reply = athleteIntelligenceService.coachChatAboutActivity(
            activityId, userId, message.trim(), goalContext);
        return ResponseEntity.ok(CoachReply.builder().reply(reply).build());
    }

    @PostMapping(value = "/{activityId}/coach/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter coachChatStream(
            @PathVariable UUID activityId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        activityService.getActivity(activityId, userId);
        String message = body == null ? null : body.get("message");
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("message is required");
        }
        String goalContext = null;
        try {
            goalContext = trainingGoalService.buildActivityCoachContextForPrompt(userId);
        } catch (Exception e) {
            log.warn("Skipping training-goal context for streaming activity coach chat: {}", e.toString());
        }
        String systemPrompt = athleteIntelligenceService.buildActivityCoachSystemPrompt(
            activityId, userId, goalContext);
        SseEmitter emitter = new SseEmitter(90_000L);
        athleteIntelligenceService.streamCoachChat(message.trim(), systemPrompt, emitter);
        return emitter;
    }
}