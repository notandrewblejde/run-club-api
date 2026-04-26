package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.dto.CreateGoalRequest;
import com.runclub.api.dto.UpdateGoalRequest;
import com.runclub.api.model.Goal;
import com.runclub.api.model.GoalProgress;
import com.runclub.api.model.LeaderboardEntry;
import com.runclub.api.service.ClubGoalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clubs/{clubId}/goals")
@Tag(name = "Club goals")
public class ClubGoalController {

    private final ClubGoalService goalService;
    private final com.runclub.api.service.GoalAttributionService goalAttributionService;

    public ClubGoalController(ClubGoalService goalService, com.runclub.api.service.GoalAttributionService goalAttributionService) {
        this.goalService = goalService;
        this.goalAttributionService = goalAttributionService;
    }

    @PostMapping
    public ResponseEntity<Goal> createGoal(
            @PathVariable UUID clubId,
            @Valid @RequestBody CreateGoalRequest body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        com.runclub.api.entity.ClubGoal created = goalService.createGoal(
            clubId, body.name, body.targetDistanceMiles,
            LocalDate.parse(body.startDate), LocalDate.parse(body.endDate), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Goal.from(created));
    }

    @GetMapping
    public ApiList<Goal> listGoals(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        Page<com.runclub.api.entity.ClubGoal> goals = goalService.getAllGoals(clubId, pageable);
        return ApiList.of(goals.getContent().stream().map(Goal::from).toList(),
            goals.hasNext(), goals.getTotalElements(), "/v1/clubs/" + clubId + "/goals");
    }

    @GetMapping("/active")
    public ApiList<Goal> listActiveGoals(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        Page<com.runclub.api.entity.ClubGoal> goals = goalService.getActiveGoals(clubId, pageable);
        return ApiList.of(goals.getContent().stream().map(Goal::from).toList(),
            goals.hasNext(), goals.getTotalElements(), "/v1/clubs/" + clubId + "/goals/active");
    }

    @PatchMapping("/{goalId}")
    public Goal updateGoal(
            @PathVariable UUID clubId,
            @PathVariable UUID goalId,
            @Valid @RequestBody UpdateGoalRequest body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        return Goal.from(goalService.updateGoal(clubId, goalId, body, userId));
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> deleteGoal(
            @PathVariable UUID clubId,
            @PathVariable UUID goalId,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        goalService.deleteGoal(clubId, goalId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{goalId}/progress")
    public GoalProgress getProgress(
            @PathVariable UUID clubId,
            @PathVariable UUID goalId) {
        return goalService.getGoalProgress(goalId);
    }

    @GetMapping("/{goalId}/leaderboard")
    public ApiList<LeaderboardEntry> getLeaderboard(
            @PathVariable UUID clubId,
            @PathVariable UUID goalId) {
        List<LeaderboardEntry> entries = goalService.getGoalLeaderboardForClub(
            clubId, goalId, 10_000);
        return ApiList.of(entries, false, entries.size(),
            "/v1/clubs/" + clubId + "/goals/" + goalId + "/leaderboard");
    }

    @PostMapping("/{goalId}/contributions")
    public ResponseEntity<Map<String, Object>> contribute(
            @PathVariable UUID clubId,
            @PathVariable UUID goalId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String activityIdStr = body.get("activity_id");
        if (activityIdStr == null || activityIdStr.isEmpty()) {
            throw com.runclub.api.api.ApiException.missingField("activity_id");
        }
        UUID userId = Auth.userId(authentication);
        com.runclub.api.entity.GoalContribution c = goalService.recordContribution(
            goalId, userId, UUID.fromString(activityIdStr));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "object", "goal_contribution",
            "id", c.getId(),
            "goal_id", c.getGoal().getId(),
            "distance_miles", c.getDistanceMiles()));
    }

    @PostMapping("/{goalId}/recalculate")
    public ResponseEntity<?> recalculate(@PathVariable UUID clubId,
                                          @PathVariable UUID goalId,
                                          Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        goalAttributionService.backfillContributionsAsync(goalId);
        return ResponseEntity.accepted().body("{\"message\": \"Recalculation triggered\"}");
    }
}
