package com.runclub.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.dto.PostTrainingGoalFeedbackRequest;
import com.runclub.api.dto.PutTrainingGoalRequest;
import com.runclub.api.entity.UserDailyTrainingPlan;
import com.runclub.api.entity.UserTrainingProfile;
import com.runclub.api.model.GoalFeedbackMessage;
import com.runclub.api.model.JsonDtos.CoachReply;
import com.runclub.api.model.JsonDtos.GoalFeedbackDeletion;
import com.runclub.api.model.TrainingGoal;
import com.runclub.api.model.TrainingToday;
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
    public TrainingGoal getTrainingGoal(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        UserTrainingProfile profile = trainingGoalService.getOrEmptyProfile(userId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Optional<UserDailyTrainingPlan> planOpt = trainingGoalService.findDailyPlan(userId, today);
        return TrainingGoal.fromProfile(profile, planOpt, objectMapper);
    }

    @PutMapping("/training-goal")
    public TrainingGoal putTrainingGoal(
            @Valid @RequestBody PutTrainingGoalRequest body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        String text = body.getGoalText() != null ? body.getGoalText() : "";
        UserTrainingProfile saved = trainingGoalService.putGoalText(userId, text);
        CompletableFuture.runAsync(() -> trainingGoalService.refreshInterpretationAndDailyPlan(userId));
        return trainingGoalFromProfile(saved, userId);
    }

    @GetMapping("/training-goal/feedback")
    public ApiList<GoalFeedbackMessage> listGoalFeedback(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int limit,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        TrainingGoalService.FeedbackListPage fp = trainingGoalService.listFeedbackPage(userId, page, limit);
        var data = fp.itemsChronological().stream().map(GoalFeedbackMessage::from).toList();
        return ApiList.of(data, fp.hasMore(), fp.totalCount(), "/v1/me/training-goal/feedback");
    }

    @PostMapping("/training-goal/feedback")
    public CoachReply postGoalFeedback(
            @Valid @RequestBody PostTrainingGoalFeedbackRequest body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        String reply = trainingGoalService.postGoalFeedback(userId, body.getMessage());
        return CoachReply.builder().reply(reply).build();
    }

    @DeleteMapping("/training-goal/feedback")
    public GoalFeedbackDeletion clearGoalFeedback(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        int deleted = trainingGoalService.clearGoalFeedback(userId);
        return GoalFeedbackDeletion.builder().deleted(deleted).build();
    }

    @GetMapping("/training-today")
    public ResponseEntity<TrainingToday> getTrainingToday(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Optional<UserDailyTrainingPlan> planOpt = trainingGoalService.findDailyPlan(userId, today);
        if (planOpt.isEmpty()) {
            trainingGoalService.refreshInterpretationAndDailyPlan(userId);
            planOpt = trainingGoalService.findDailyPlan(userId, today);
        }
        if (planOpt.isEmpty()) {
            return ResponseEntity.ok(TrainingToday.fallbackNoPlanCached());
        }
        return ResponseEntity.ok(TrainingToday.fromPlan(planOpt.get(), objectMapper));
    }

    private TrainingGoal trainingGoalFromProfile(UserTrainingProfile profile, UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Optional<UserDailyTrainingPlan> planOpt = trainingGoalService.findDailyPlan(userId, today);
        return TrainingGoal.fromProfile(profile, planOpt, objectMapper);
    }
}
