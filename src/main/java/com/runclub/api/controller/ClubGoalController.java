package com.runclub.api.controller;

import com.runclub.api.entity.ClubGoal;
import com.runclub.api.entity.GoalContribution;
import com.runclub.api.service.ClubGoalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clubs/{clubId}/goals")
public class ClubGoalController {

    private final ClubGoalService clubGoalService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    public ClubGoalController(ClubGoalService clubGoalService) {
        this.clubGoalService = clubGoalService;
    }

    @PostMapping
    public ResponseEntity<?> createGoal(
            @PathVariable UUID clubId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            UUID userId = UUID.nameUUIDFromBytes(auth0Id.getBytes());

            String name = (String) request.get("name");
            BigDecimal targetMiles = request.get("target_distance_miles") != null
                ? new BigDecimal(request.get("target_distance_miles").toString())
                : null;
            LocalDate startDate = LocalDate.parse((String) request.get("start_date"), DATE_FORMATTER);
            LocalDate endDate = LocalDate.parse((String) request.get("end_date"), DATE_FORMATTER);

            if (name == null || name.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Goal name is required"));
            }

            ClubGoal goal = clubGoalService.createGoal(clubId, name, targetMiles, startDate, endDate, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("id", goal.getId());
            response.put("name", goal.getName());
            response.put("target_distance_miles", goal.getTargetDistanceMiles());
            response.put("start_date", goal.getStartDate());
            response.put("end_date", goal.getEndDate());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{goalId}/contribute")
    public ResponseEntity<?> recordContribution(
            @PathVariable UUID clubId,
            @PathVariable UUID goalId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            UUID userId = UUID.nameUUIDFromBytes(auth0Id.getBytes());

            String activityIdStr = request.get("activity_id");
            if (activityIdStr == null || activityIdStr.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "activity_id is required"));
            }

            UUID activityId = UUID.fromString(activityIdStr);
            GoalContribution contribution = clubGoalService.recordContribution(goalId, userId, activityId);

            Map<String, Object> response = new HashMap<>();
            response.put("contribution_id", contribution.getId());
            response.put("goal_id", contribution.getGoal().getId());
            response.put("distance_miles", contribution.getDistanceMiles());
            response.put("contributed_at", contribution.getContributedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{goalId}/progress")
    public ResponseEntity<?> getProgress(
            @PathVariable UUID clubId,
            @PathVariable UUID goalId) {
        try {
            Map<String, Object> progress = clubGoalService.getGoalProgress(goalId);
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{goalId}/leaderboard")
    public ResponseEntity<?> getLeaderboard(
            @PathVariable UUID clubId,
            @PathVariable UUID goalId) {
        try {
            List<Map<String, Object>> leaderboard = clubGoalService.getGoalLeaderboard(goalId);

            Map<String, Object> response = new HashMap<>();
            response.put("goal_id", goalId);
            response.put("members", leaderboard);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveGoals(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Pageable pageable = PageRequest.of(page - 1, Math.min(limit, 100));
            Page<ClubGoal> goals = clubGoalService.getActiveGoals(clubId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("goals", goals.map(g -> Map.of(
                "id", g.getId(),
                "name", g.getName(),
                "target_distance_miles", g.getTargetDistanceMiles(),
                "start_date", g.getStartDate(),
                "end_date", g.getEndDate()
            )).getContent());
            response.put("total", goals.getTotalElements());
            response.put("page", goals.getNumber() + 1);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllGoals(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Pageable pageable = PageRequest.of(page - 1, Math.min(limit, 100));
            Page<ClubGoal> goals = clubGoalService.getAllGoals(clubId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("goals", goals.map(g -> Map.of(
                "id", g.getId(),
                "name", g.getName(),
                "target_distance_miles", g.getTargetDistanceMiles(),
                "start_date", g.getStartDate(),
                "end_date", g.getEndDate()
            )).getContent());
            response.put("total", goals.getTotalElements());
            response.put("page", goals.getNumber() + 1);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
