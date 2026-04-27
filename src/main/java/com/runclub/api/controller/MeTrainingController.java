package com.runclub.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.dto.PostTrainingGoalFeedbackRequest;
import com.runclub.api.dto.PutTrainingGoalRequest;
import com.runclub.api.entity.UserDailyTrainingPlan;
import com.runclub.api.entity.UserTrainingGoalFeedback;
import com.runclub.api.entity.UserTrainingProfile;
import com.runclub.api.service.TrainingGoalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/v1/me")
@Tag(name = "Me / Training")
public class MeTrainingController {

    private final TrainingGoalService trainingGoalService;
    private final ObjectMapper objectMapper;

    public MeTrainingController(TrainingGoalService trainingGoalService, ObjectMapper objectMapper) {
        this.trainingGoalService = trainingGoalService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/training-goal")
    public Map<String, Object> getTrainingGoal(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        UserTrainingProfile profile = trainingGoalService.getOrEmptyProfile(userId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Optional<UserDailyTrainingPlan> planOpt = trainingGoalService.findDailyPlan(userId, today);

        Map<String, Object> out = new HashMap<>();
        out.put("goal_text", profile.getGoalText() != null ? profile.getGoalText() : "");
        out.put("interpretation_json", profile.getInterpretationJson());
        out.put("interpretation_updated_at",
            profile.getInterpretationUpdatedAt() != null ? profile.getInterpretationUpdatedAt().toString() : null);
        if (profile.getInterpretationJson() != null && !profile.getInterpretationJson().isBlank()) {
            try {
                out.put("interpretation", objectMapper.readValue(profile.getInterpretationJson(), Map.class));
            } catch (Exception e) {
                out.put("interpretation", null);
            }
        } else {
            out.put("interpretation", null);
        }
        out.put("daily_plan", planOpt.map(this::dailyPlanToMap).orElse(null));
        return out;
    }

    @PutMapping("/training-goal")
    public Map<String, Object> putTrainingGoal(
            @Valid @RequestBody PutTrainingGoalRequest body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        String text = body.getGoalText() != null ? body.getGoalText() : "";
        UserTrainingProfile saved = trainingGoalService.putGoalText(userId, text);
        CompletableFuture.runAsync(() -> trainingGoalService.refreshInterpretationAndDailyPlan(userId));
        return getTrainingGoalResponseFromProfile(saved, userId);
    }

    @GetMapping("/training-goal/feedback")
    public ApiList<Map<String, Object>> listGoalFeedback(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int limit,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        TrainingGoalService.FeedbackListPage fp = trainingGoalService.listFeedbackPage(userId, page, limit);
        List<Map<String, Object>> data = fp.itemsChronological().stream()
            .map(MeTrainingController::feedbackToMap)
            .toList();
        return ApiList.of(data, fp.hasMore(), fp.totalCount(), "/v1/me/training-goal/feedback");
    }

    @PostMapping("/training-goal/feedback")
    public Map<String, String> postGoalFeedback(
            @Valid @RequestBody PostTrainingGoalFeedbackRequest body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        String reply = trainingGoalService.postGoalFeedback(userId, body.getMessage());
        return Map.of("reply", reply);
    }

    @DeleteMapping("/training-goal/feedback")
    public Map<String, Object> clearGoalFeedback(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        int deleted = trainingGoalService.clearGoalFeedback(userId);
        return Map.of("deleted", deleted);
    }

    private static Map<String, Object> feedbackToMap(UserTrainingGoalFeedback f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", f.getId().toString());
        m.put("role", f.getRole());
        m.put("content", f.getContent());
        m.put("created_at", f.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> getTrainingGoalResponseFromProfile(UserTrainingProfile profile, UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Optional<UserDailyTrainingPlan> planOpt = trainingGoalService.findDailyPlan(userId, today);
        Map<String, Object> out = new HashMap<>();
        out.put("goal_text", profile.getGoalText() != null ? profile.getGoalText() : "");
        out.put("interpretation_json", profile.getInterpretationJson());
        out.put("interpretation_updated_at",
            profile.getInterpretationUpdatedAt() != null ? profile.getInterpretationUpdatedAt().toString() : null);
        if (profile.getInterpretationJson() != null && !profile.getInterpretationJson().isBlank()) {
            try {
                out.put("interpretation", objectMapper.readValue(profile.getInterpretationJson(), Map.class));
            } catch (Exception e) {
                out.put("interpretation", null);
            }
        } else {
            out.put("interpretation", null);
        }
        out.put("daily_plan", planOpt.map(this::dailyPlanToMap).orElse(null));
        return out;
    }

    @GetMapping("/training-today")
    public ResponseEntity<Map<String, Object>> getTrainingToday(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Optional<UserDailyTrainingPlan> planOpt = trainingGoalService.findDailyPlan(userId, today);
        if (planOpt.isEmpty()) {
            trainingGoalService.refreshInterpretationAndDailyPlan(userId);
            planOpt = trainingGoalService.findDailyPlan(userId, today);
        }
        if (planOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "headline", "Today's movement",
                "bullets", java.util.List.of("Set a training goal in AI Coach for a tailored plan."),
                "progress_hint", "",
                "primary_session", "Easy day or rest as you prefer.",
                "rationale", "No plan cached yet."
            ));
        }
        UserDailyTrainingPlan plan = planOpt.get();
        JsonNode body;
        try {
            body = objectMapper.readTree(plan.getBodyJson());
        } catch (Exception e) {
            body = objectMapper.createObjectNode();
        }
        java.util.List<String> bullets = new java.util.ArrayList<>();
        if (body.path("bullets").isArray()) {
            for (JsonNode b : body.path("bullets")) {
                bullets.add(b.asText());
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("headline", plan.getHeadline());
        m.put("bullets", bullets);
        m.put("progress_hint", body.path("progress_hint").asText(""));
        m.put("primary_session", body.path("primary_session").asText(""));
        m.put("rationale", body.path("rationale").asText(""));
        m.put("plan_date", plan.getPlanDate().toString());
        m.put("generated_at", plan.getGeneratedAt().toString());
        return ResponseEntity.ok(m);
    }

    private Map<String, Object> dailyPlanToMap(UserDailyTrainingPlan plan) {
        Map<String, Object> m = new HashMap<>();
        m.put("plan_date", plan.getPlanDate().toString());
        m.put("headline", plan.getHeadline());
        m.put("generated_at", plan.getGeneratedAt().toString());
        try {
            m.put("body", objectMapper.readValue(plan.getBodyJson(), Map.class));
        } catch (Exception e) {
            m.put("body", Map.of());
        }
        return m;
    }
}
