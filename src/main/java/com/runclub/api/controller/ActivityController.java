package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.model.Activity;
import com.runclub.api.service.ActivityService;
import com.runclub.api.service.AthleteIntelligenceService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public ActivityController(ActivityService activityService, AthleteIntelligenceService athleteIntelligenceService) {
        this.activityService = activityService;
        this.athleteIntelligenceService = athleteIntelligenceService;
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

    @GetMapping("/{activityId}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable UUID activityId,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        // Authorization piggybacks on getActivity (throws 404/403 if needed).
        activityService.getActivity(activityId, userId);
        String summary = athleteIntelligenceService.generateActivitySummary(activityId);
        return ResponseEntity.ok(Map.of("activity_id", activityId, "summary", summary));
    }
}
