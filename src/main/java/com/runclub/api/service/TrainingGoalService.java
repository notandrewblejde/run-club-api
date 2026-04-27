package com.runclub.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.UserDailyTrainingPlan;
import com.runclub.api.entity.UserTrainingGoalFeedback;
import com.runclub.api.entity.UserTrainingProfile;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserDailyTrainingPlanRepository;
import com.runclub.api.repository.UserTrainingGoalFeedbackRepository;
import com.runclub.api.repository.UserTrainingProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class TrainingGoalService {

    private static final Logger logger = Logger.getLogger(TrainingGoalService.class.getName());

    private static final int STATS_ACTIVITY_LIMIT = 50;
    private static final int FEEDBACK_TRANSCRIPT_MAX_CHARS = 12_000;
    private static final int FEEDBACK_CONTEXT_MESSAGES = 40;

    private final UserTrainingProfileRepository profileRepository;
    private final UserDailyTrainingPlanRepository dailyPlanRepository;
    private final ActivityRepository activityRepository;
    private final UserTrainingGoalFeedbackRepository feedbackRepository;
    private final AthleteIntelligenceService athleteIntelligenceService;
    private final ObjectMapper objectMapper;

    public TrainingGoalService(UserTrainingProfileRepository profileRepository,
                               UserDailyTrainingPlanRepository dailyPlanRepository,
                               ActivityRepository activityRepository,
                               UserTrainingGoalFeedbackRepository feedbackRepository,
                               AthleteIntelligenceService athleteIntelligenceService,
                               ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.dailyPlanRepository = dailyPlanRepository;
        this.activityRepository = activityRepository;
        this.feedbackRepository = feedbackRepository;
        this.athleteIntelligenceService = athleteIntelligenceService;
        this.objectMapper = objectMapper;
    }

    /**
     * Best-effort refresh after Strava activity upsert (runs on a background thread).
     */
    public void refreshAfterActivitySync(UUID userId) {
        try {
            refreshInterpretationAndDailyPlan(userId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Training plan refresh failed for user " + userId, e);
        }
    }

    @Transactional
    public UserTrainingProfile putGoalText(UUID userId, String goalText) {
        String normalized = goalText == null ? "" : goalText.trim();
        UserTrainingProfile profile = profileRepository.findById(userId).orElseGet(() -> {
            UserTrainingProfile p = new UserTrainingProfile();
            p.setUserId(userId);
            return p;
        });
        profile.setGoalText(normalized);
        profile.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        UserTrainingProfile saved = profileRepository.save(profile);
        return saved;
    }

    /**
     * Recomputes interpretation (when goal non-empty) and today's UTC daily plan row.
     */
    @Transactional
    public void refreshInterpretationAndDailyPlan(UUID userId) {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        UserTrainingProfile profile = profileRepository.findById(userId).orElseGet(() -> {
            UserTrainingProfile p = new UserTrainingProfile();
            p.setUserId(userId);
            return p;
        });

        String goal = profile.getGoalText() == null ? "" : profile.getGoalText().trim();
        String statsJson = buildRollingStatsJson(userId);
        String inputsHash = sha256Hex(goal + "\n" + statsJson + "\n" + todayUtc);

        if (goal.isEmpty()) {
            profile.setInterpretationJson(null);
            profile.setInterpretationUpdatedAt(null);
        } else {
            String feedbackTranscript = truncateTail(
                buildChronologicalTranscript(userId, FEEDBACK_CONTEXT_MESSAGES),
                FEEDBACK_TRANSCRIPT_MAX_CHARS);
            String rawInterp = athleteIntelligenceService.interpretTrainingGoal(
                goal,
                statsJson,
                feedbackTranscript.isBlank() ? null : feedbackTranscript);
            String interp = sanitizeInterpretationJson(rawInterp);
            profile.setInterpretationJson(interp);
            profile.setInterpretationUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        profile.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        profileRepository.save(profile);

        String interpForDaily = profile.getInterpretationJson() != null && !profile.getInterpretationJson().isBlank()
            ? profile.getInterpretationJson()
            : "{}";
        String rawDaily = athleteIntelligenceService.dailyTrainingPlanJson(interpForDaily, statsJson, todayUtc.toString());
        String bodyJson = validateAndNormalizeDailyJson(rawDaily);

        UserDailyTrainingPlan plan = dailyPlanRepository.findByUserIdAndPlanDate(userId, todayUtc)
            .orElseGet(() -> {
                UserDailyTrainingPlan p = new UserDailyTrainingPlan();
                p.setUserId(userId);
                p.setPlanDate(todayUtc);
                return p;
            });
        try {
            JsonNode root = objectMapper.readTree(bodyJson);
            plan.setHeadline(root.path("headline").asText("Today's movement"));
        } catch (Exception e) {
            plan.setHeadline("Today's movement");
        }
        plan.setBodyJson(bodyJson);
        plan.setInputsHash(inputsHash);
        plan.setGeneratedAt(LocalDateTime.now(ZoneOffset.UTC));
        dailyPlanRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public UserTrainingProfile getOrEmptyProfile(UUID userId) {
        return profileRepository.findById(userId).orElseGet(() -> {
            UserTrainingProfile p = new UserTrainingProfile();
            p.setUserId(userId);
            p.setGoalText("");
            return p;
        });
    }

    @Transactional(readOnly = true)
    public java.util.Optional<UserDailyTrainingPlan> findDailyPlan(UUID userId, LocalDate date) {
        return dailyPlanRepository.findByUserIdAndPlanDate(userId, date);
    }

    /**
     * Appends a user feedback line, generates a coach reply (stored), then refreshes interpretation + daily plan.
     * Feedback is persisted in Postgres and folded into later interpretation passes—not a separate Claude "memory" API.
     */
    @Transactional
    public String postGoalFeedback(UUID userId, String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("message is required");
        }
        if (trimmed.length() > 4000) {
            throw ApiException.badRequest("message is too long");
        }
        UserTrainingProfile profile = getOrEmptyProfile(userId);
        String goal = profile.getGoalText() == null ? "" : profile.getGoalText().trim();
        if (goal.isEmpty()) {
            throw ApiException.badRequest("Set a training goal first, then you can add clarifying feedback.");
        }

        UserTrainingGoalFeedback userRow = new UserTrainingGoalFeedback();
        userRow.setUserId(userId);
        userRow.setRole(UserTrainingGoalFeedback.ROLE_USER);
        userRow.setContent(trimmed);
        userRow.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        feedbackRepository.save(userRow);

        String statsJson = buildRollingStatsJson(userId);
        String chronological = buildChronologicalTranscript(userId, FEEDBACK_CONTEXT_MESSAGES);
        String reply = athleteIntelligenceService.trainingGoalFeedbackReply(goal, statsJson, chronological);
        if (reply.length() > 8000) {
            reply = reply.substring(0, 8000);
        }

        UserTrainingGoalFeedback assistantRow = new UserTrainingGoalFeedback();
        assistantRow.setUserId(userId);
        assistantRow.setRole(UserTrainingGoalFeedback.ROLE_ASSISTANT);
        assistantRow.setContent(reply);
        assistantRow.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        feedbackRepository.save(assistantRow);

        refreshInterpretationAndDailyPlan(userId);
        return reply;
    }

    /**
     * One page of feedback, newest-toward-oldest: page 1 is the most recent {@code limit} messages
     * in chronological order; page 2 is the next older block, etc.
     */
    @Transactional(readOnly = true)
    public FeedbackListPage listFeedbackPage(UUID userId, int page, int limit) {
        int p = Math.max(page, 1) - 1;
        int lim = Math.min(Math.max(limit, 1), 80);
        var result = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(p, lim));
        List<UserTrainingGoalFeedback> rows = new ArrayList<>(result.getContent());
        Collections.reverse(rows);
        return new FeedbackListPage(
            rows,
            result.getTotalElements(),
            result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public long countGoalFeedback(UUID userId) {
        return feedbackRepository.countByUserId(userId);
    }

    @Transactional
    public int clearGoalFeedback(UUID userId) {
        int n = feedbackRepository.deleteByUserId(userId);
        refreshInterpretationAndDailyPlan(userId);
        return n;
    }

    public record FeedbackListPage(
        List<UserTrainingGoalFeedback> itemsChronological,
        long totalCount,
        boolean hasMore
    ) {}

    /**
     * Goal text plus recent goal-feedback thread for per-activity coach chat prompts (read-only; avoids a
     * service cycle by being called from {@link com.runclub.api.controller.ActivityController}).
     */
    private static final int COACH_CHAT_GOAL_CONTEXT_MESSAGES = 32;
    private static final int COACH_CHAT_GOAL_CONTEXT_MAX_CHARS = 10_000;

    @Transactional(readOnly = true)
    public String buildActivityCoachContextForPrompt(UUID userId) {
        UserTrainingProfile profile = getOrEmptyProfile(userId);
        String goal = profile.getGoalText() == null ? "" : profile.getGoalText().trim();
        String feedback = truncateTail(
            buildChronologicalTranscript(userId, COACH_CHAT_GOAL_CONTEXT_MESSAGES),
            COACH_CHAT_GOAL_CONTEXT_MAX_CHARS);
        if (goal.isEmpty() && (feedback == null || feedback.isBlank())) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (!goal.isEmpty()) {
            sb.append("Stated training goal: \"").append(goal).append("\"\n\n");
        }
        if (feedback != null && !feedback.isBlank()) {
            sb.append("Recent clarifications about that goal (chronological; use as intent when it fits this workout):\n")
                .append(feedback);
        }
        return sb.toString();
    }

    /** Rolling activity stats JSON (same source as training-plan prompts). */
    @Transactional(readOnly = true)
    public String rollingStatsJsonForUser(UUID userId) {
        return buildRollingStatsJson(userId);
    }

    private String buildChronologicalTranscript(UUID userId, int maxMessages) {
        var page = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, maxMessages));
        List<UserTrainingGoalFeedback> rows = new ArrayList<>(page.getContent());
        Collections.reverse(rows);
        StringBuilder sb = new StringBuilder();
        for (UserTrainingGoalFeedback f : rows) {
            String label = UserTrainingGoalFeedback.ROLE_USER.equals(f.getRole()) ? "Athlete" : "Coach";
            sb.append(label).append(": ").append(f.getContent()).append("\n");
        }
        return sb.toString();
    }

    private static String truncateTail(String s, int maxChars) {
        if (s == null || s.length() <= maxChars) {
            return s == null ? "" : s;
        }
        return s.substring(s.length() - maxChars);
    }

    private String buildRollingStatsJson(UUID userId) {
        List<Activity> acts = activityRepository.findByUser_IdOrderByStartDateDesc(
            userId, PageRequest.of(0, STATS_ACTIVITY_LIMIT));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime start7 = now.minusDays(7);
        LocalDateTime start30 = now.minusDays(30);

        int sessions7 = 0;
        BigDecimal miles7 = BigDecimal.ZERO;
        int sessions30 = 0;
        int prCount30 = 0;
        BigDecimal longestMiles30 = BigDecimal.ZERO;
        Activity mostRecent = acts.isEmpty() ? null : acts.get(0);

        for (Activity a : acts) {
            LocalDateTime sd = a.getStartDate();
            if (sd == null) {
                continue;
            }
            BigDecimal miles = a.getDistanceMiles() != null ? a.getDistanceMiles() : BigDecimal.ZERO;
            if (!sd.isBefore(start30)) {
                sessions30++;
                if (miles.compareTo(longestMiles30) > 0) {
                    longestMiles30 = miles;
                }
                if (Boolean.TRUE.equals(a.getIsPersonalRecord())) {
                    prCount30++;
                }
            }
            if (!sd.isBefore(start7)) {
                sessions7++;
                miles7 = miles7.add(miles);
            }
        }

        Long daysSinceLastRun = null;
        if (mostRecent != null && mostRecent.getStartDate() != null) {
            daysSinceLastRun = ChronoUnit.DAYS.between(mostRecent.getStartDate().toLocalDate(), now.toLocalDate());
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("window_activities_loaded", acts.size());
        root.put("sessions_last_7_days", sessions7);
        root.put("miles_last_7_days", miles7.doubleValue());
        root.put("sessions_last_30_days", sessions30);
        root.put("longest_run_miles_last_30_days", longestMiles30.doubleValue());
        root.put("personal_record_flags_last_30_days", prCount30);
        if (daysSinceLastRun != null) {
            root.put("days_since_last_run", daysSinceLastRun);
        } else {
            root.putNull("days_since_last_run");
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String sanitizeInterpretationJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultInterpretationJson();
        }
        String t = raw.trim();
        if (t.startsWith("AI summary unavailable") || t.startsWith("Error generating") || t.startsWith("Unable to generate")) {
            return defaultInterpretationJson();
        }
        try {
            JsonNode n = objectMapper.readTree(t);
            if (!n.isObject() || !n.path("summary").isTextual()) {
                return defaultInterpretationJson();
            }
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            return defaultInterpretationJson();
        }
    }

    private static String defaultInterpretationJson() {
        return "{\"summary\":\"Goal captured; detailed coaching will refine as you log more runs.\","
            + "\"themes\":[\"consistency\",\"recovery\"],\"constraints\":\"This is general guidance, not medical advice.\"}";
    }

    private String validateAndNormalizeDailyJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultDailyPlanJson();
        }
        String t = raw.trim();
        if (t.startsWith("AI summary unavailable") || t.startsWith("Error generating") || t.startsWith("Unable to generate")) {
            return defaultDailyPlanJson();
        }
        try {
            JsonNode n = objectMapper.readTree(t);
            if (!n.isObject()) {
                return defaultDailyPlanJson();
            }
            ObjectNode out = objectMapper.createObjectNode();
            String headline = n.path("headline").asText("").trim();
            out.put("headline", headline.isEmpty() ? "Today's movement" : headline);
            String primary = n.path("primary_session").asText("").trim();
            if (primary.isEmpty()) {
                primary = n.path("primary_recommendation").asText("").trim();
            }
            out.put("primary_session", primary.isEmpty() ? "Easy movement or rest as feels right" : primary);
            out.put("rationale", n.path("rationale").asText("Based on your recent volume and goal."));
            out.put("progress_hint", n.path("progress_hint").asText(""));
            ArrayNode bullets = objectMapper.createArrayNode();
            if (n.path("bullets").isArray()) {
                for (JsonNode b : n.path("bullets")) {
                    if (bullets.size() >= 6) {
                        break;
                    }
                    String s = b.asText("").trim();
                    if (!s.isEmpty()) {
                        bullets.add(s);
                    }
                }
            }
            if (bullets.isEmpty()) {
                bullets.add("Hydrate through the day");
                bullets.add("Prioritize sleep when you can");
            }
            out.set("bullets", bullets);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            return defaultDailyPlanJson();
        }
    }

    private static String defaultDailyPlanJson() {
        return "{\"headline\":\"Today's movement\","
            + "\"primary_session\":\"Easy jog, walk, or rest—pick what feels sustainable.\","
            + "\"rationale\":\"We could not load AI details just now; here is a gentle default.\","
            + "\"progress_hint\":\"Log runs to sharpen this plan.\","
            + "\"bullets\":[\"Hydrate as thirst guides you\",\"Include protein in meals if it fits your day\","
            + "\"Extra sleep often helps recovery\"]}";
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "0";
        }
    }
}
