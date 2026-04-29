package com.runclub.api.service;

import com.runclub.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fires at 8:00 AM Eastern Time (13:00 UTC) every day.
 * Fetches coach recommendations for opted-in users and sends push notifications.
 */
@Component
public class DailyCoachTipScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyCoachTipScheduler.class);

    private final UserRepository userRepo;
    private final AthleteIntelligenceService coachService;
    private final PushNotificationService pushService;
    private final TrainingGoalService trainingGoalService;

    public DailyCoachTipScheduler(UserRepository userRepo,
                                   AthleteIntelligenceService coachService,
                                   PushNotificationService pushService,
                                   TrainingGoalService trainingGoalService) {
        this.userRepo = userRepo;
        this.coachService = coachService;
        this.pushService = pushService;
        this.trainingGoalService = trainingGoalService;
    }

    // 8:00 AM Eastern = 13:00 UTC (non-DST) / 12:00 UTC (DST)
    // Use 13:00 UTC — close enough year-round
    @Scheduled(cron = "0 0 13 * * *", zone = "UTC")
    public void sendDailyCoachTips() {
        log.info("Daily coach tip job starting");
        try {
            // Get all users who have Strava connected (active users)
            List<UUID> userIds = userRepo.findAll().stream()
                .filter(u -> u.getStravaAccessToken() != null)
                .map(u -> u.getId())
                .collect(Collectors.toList());

            log.info("Daily coach tip: {} eligible users", userIds.size());

            // Generate tips in batches to avoid overwhelming Claude
            Map<UUID, String> tips = new HashMap<>();
            for (UUID userId : userIds) {
                try {
                    String rollingStats = trainingGoalService.rollingStatsJsonForUser(userId);
                    String goalContext = null;
                    try { goalContext = trainingGoalService.buildActivityCoachContextForPrompt(userId); }
                    catch (Exception ignored) {}

                    // Get a short coach recommendation
                    String prompt = buildDailyTipPrompt(rollingStats, goalContext);
                    String tip = coachService.coachChatGlobal(userId,
                        "Give me one specific recommendation for today's training in 1-2 sentences.",
                        rollingStats, goalContext);
                    if (tip != null && !tip.isBlank()) {
                        tips.put(userId, tip);
                    }
                    // Small delay to avoid rate limiting
                    Thread.sleep(200);
                } catch (Exception e) {
                    log.debug("Could not generate tip for user {}: {}", userId, e.getMessage());
                }
            }

            log.info("Daily coach tip: generated {} tips, sending push notifications", tips.size());
            pushService.sendDailyCoachTipsAsync(userIds, tips);

        } catch (Exception e) {
            log.error("Daily coach tip job failed", e);
        }
    }

    private String buildDailyTipPrompt(String stats, String goalContext) {
        return "Based on recent training, give one specific actionable recommendation for today. Be brief (1-2 sentences max).";
    }
}
