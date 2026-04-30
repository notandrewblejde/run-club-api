package com.runclub.api.controller;

import com.runclub.api.api.ApiException;
import com.runclub.api.api.Auth;
import com.runclub.api.model.JsonDtos.CoachReply;
import com.runclub.api.service.AthleteIntelligenceService;
import com.runclub.api.service.TrainingGoalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/me/ai")
@Tag(name = "Me / AI")
public class MeAiController {

    private static final Logger log = LoggerFactory.getLogger(MeAiController.class);

    private final TrainingGoalService trainingGoalService;
    private final AthleteIntelligenceService athleteIntelligenceService;

    public MeAiController(TrainingGoalService trainingGoalService, AthleteIntelligenceService athleteIntelligenceService) {
        this.trainingGoalService = trainingGoalService;
        this.athleteIntelligenceService = athleteIntelligenceService;
    }

    /**
     * General coaching chat using recent activities + roll-up stats and optional goal / goal-feedback context.
     */
    @PostMapping("/chat")
    public ResponseEntity<CoachReply> globalChat(
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        String message = body == null ? null : body.get("message");
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("message is required");
        }
        String trimmed = message.trim();
        if (trimmed.length() > 4000) {
            throw ApiException.badRequest("message is too long");
        }
        String stats = trainingGoalService.rollingStatsJsonForUser(userId);
        String goalContext = null;
        try {
            goalContext = trainingGoalService.buildActivityCoachContextForPrompt(userId);
        } catch (Exception e) {
            log.warn("Skipping training-goal context for global AI chat: {}", e.toString());
        }
        String reply = athleteIntelligenceService.coachChatGlobal(userId, trimmed, stats, goalContext);
        return ResponseEntity.ok(CoachReply.builder().reply(reply).build());
    }

    /**
     * Streaming version of /chat — returns SSE tokens in real-time.
     * Each token arrives as: data: <token>\n\n
     * Final event: event: done\ndata: [DONE]\n\n
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter globalChatStream(
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        String message = body == null ? null : body.get("message");
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("message is required");
        }
        String trimmed = message.trim();
        if (trimmed.length() > 4000) {
            throw ApiException.badRequest("message is too long");
        }

        String stats = trainingGoalService.rollingStatsJsonForUser(userId);
        String goalContext = null;
        try {
            goalContext = trainingGoalService.buildActivityCoachContextForPrompt(userId);
        } catch (Exception e) {
            log.warn("Skipping training-goal context for streaming AI chat: {}", e.toString());
        }

        String systemPrompt = athleteIntelligenceService.buildGlobalCoachSystemPrompt(userId, stats, goalContext);

        SseEmitter emitter = new SseEmitter(90_000L); // 90s timeout
        athleteIntelligenceService.streamCoachChat(trimmed, systemPrompt, emitter);
        return emitter;
    }
}