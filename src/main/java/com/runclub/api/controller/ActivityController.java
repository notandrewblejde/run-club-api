package com.runclub.api.controller;

import com.runclub.api.entity.Activity;
import com.runclub.api.service.ActivityService;
import com.runclub.api.service.AthleteIntelligenceService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/activities")
public class ActivityController {

    private final ActivityService activityService;
    private final AthleteIntelligenceService athleteIntelligenceService;

    public ActivityController(ActivityService activityService, AthleteIntelligenceService athleteIntelligenceService) {
        this.activityService = activityService;
        this.athleteIntelligenceService = athleteIntelligenceService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listActivities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String userId = jwt.getClaimAsString("sub");

            UUID userUuid = UUID.nameUUIDFromBytes(userId.getBytes());

            Page<Activity> activities = activityService.getUserActivities(userUuid, page, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("activities", activities.getContent());
            response.put("total", activities.getTotalElements());
            response.put("page", activities.getNumber() + 1);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{activityId}")
    public ResponseEntity<Map<String, Object>> getActivityDetail(
            @PathVariable UUID activityId,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String userId = jwt.getClaimAsString("sub");

            UUID userUuid = UUID.nameUUIDFromBytes(userId.getBytes());

            Map<String, Object> activityDetail = activityService.getActivityDetail(activityId, userUuid);

            return ResponseEntity.ok(activityDetail);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Forbidden")) {
                return ResponseEntity.status(403).body(new HashMap<>(Map.of("error", e.getMessage())));
            }
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{activityId}/summary")
    public ResponseEntity<Map<String, Object>> getActivitySummary(
            @PathVariable UUID activityId,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String userId = jwt.getClaimAsString("sub");

            UUID userUuid = UUID.nameUUIDFromBytes(userId.getBytes());
            activityService.getActivityDetail(activityId, userUuid);

            String summary = athleteIntelligenceService.generateActivitySummary(activityId);

            Map<String, Object> response = new HashMap<>();
            response.put("activity_id", activityId);
            response.put("summary", summary);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Forbidden")) {
                return ResponseEntity.status(403).body(new HashMap<>(Map.of("error", e.getMessage())));
            }
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
