package com.runclub.api.controller;

import com.runclub.api.entity.Activity;
import com.runclub.api.service.ActivityService;
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

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listActivities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String userId = jwt.getClaimAsString("sub");

            // For now, extract UUID from sub claim - in production, this would be the user's UUID
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
}
