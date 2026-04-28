package com.runclub.api.service;

import com.runclub.api.dto.health.HealthWorkoutImportItem;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Upserts runs from Apple Health or Android Health Connect (device → API JSON).
 */
@Service
public class HealthActivityImportService {

    private static final Logger logger = Logger.getLogger(HealthActivityImportService.class.getName());

    private final ActivityRepository activityRepository;
    private final GoalAttributionService goalAttributionService;
    private final AthleteIntelligenceService athleteIntelligenceService;
    private final TrainingGoalService trainingGoalService;
    private final NotificationService notificationService;

    public HealthActivityImportService(ActivityRepository activityRepository,
                                       GoalAttributionService goalAttributionService,
                                       AthleteIntelligenceService athleteIntelligenceService,
                                       TrainingGoalService trainingGoalService,
                                       NotificationService notificationService) {
        this.activityRepository = activityRepository;
        this.goalAttributionService = goalAttributionService;
        this.athleteIntelligenceService = athleteIntelligenceService;
        this.trainingGoalService = trainingGoalService;
        this.notificationService = notificationService;
    }

    public record ImportResult(int imported, int skipped) {}

    @Transactional
    public ImportResult importWorkouts(User user, List<HealthWorkoutImportItem> items) {
        if (items == null || items.isEmpty()) {
            return new ImportResult(0, 0);
        }
        int imported = 0;
        int skipped = 0;
        for (HealthWorkoutImportItem item : items) {
            try {
                upsertOne(user, item);
                imported++;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Skip health workout " + item.getExternalId(), e);
                skipped++;
            }
        }
        return new ImportResult(imported, skipped);
    }

    private void upsertOne(User user, HealthWorkoutImportItem item) {
        String source = item.getImportSource();
        String ext = item.getExternalId();
        UUID uid = user.getId();

        Activity activity = activityRepository
            .findByUser_IdAndImportSourceAndImportExternalId(uid, source, ext)
            .orElseGet(Activity::new);

        if (activity.getId() == null) {
            activity.setUser(user);
        }

        activity.setStravaActivityId(null);
        activity.setImportSource(source);
        activity.setImportExternalId(ext);
        activity.setName(item.getName());
        activity.setType("Run");

        if (item.getDistanceMeters() != null) {
            activity.setDistanceMeters(item.getDistanceMeters().setScale(2, RoundingMode.HALF_UP));
        }
        activity.setMovingTimeSeconds(item.getMovingTimeSeconds());
        activity.setElapsedTimeSeconds(item.getMovingTimeSeconds());
        activity.setMapPolyline(item.getMapPolyline());

        LocalDateTime start = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(item.getStartDateEpochSeconds()), ZoneOffset.UTC);
        activity.setStartDate(start);

        if (activity.getCreatedAt() == null) {
            activity.setCreatedAt(LocalDateTime.now());
        }
        if (activity.getKudosCount() == null) {
            activity.setKudosCount(0);
        }
        if (activity.getCommentCount() == null) {
            activity.setCommentCount(0);
        }
        if (activity.getIsPersonalRecord() == null) {
            activity.setIsPersonalRecord(false);
        }

        Activity saved = activityRepository.save(activity);
        goalAttributionService.attributeToActiveGoals(saved);
        UUID savedId = saved.getId();
        UUID ownerId = user.getId();
        CompletableFuture.runAsync(() -> {
            athleteIntelligenceService.maybeGenerateCoachSummaryAsync(savedId);
            trainingGoalService.refreshAfterActivitySync(ownerId);
            notificationService.createActivityArrivedNotificationAsync(ownerId, savedId);
        });
    }
}
