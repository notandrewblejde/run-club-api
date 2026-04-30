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
                if ("CROSS_SOURCE_DUPLICATE".equals(e.getMessage())) {
                    logger.info("Skipped cross-source duplicate: " + item.getExternalId());
                } else {
                    logger.log(Level.WARNING, "Skip health workout " + item.getExternalId(), e);
                }
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
            .orElse(null);

        // Cross-source dedup: if no existing record for this source/externalId,
        // check if a Strava (or other-source) activity already covers the same run
        // Match: same user, start_date within ±5 minutes, distance within ±5%
        if (activity == null && item.getStartDateEpochSeconds() != null) {
            java.time.LocalDateTime itemStart = java.time.LocalDateTime.ofEpochSecond(
                item.getStartDateEpochSeconds(), 0, java.time.ZoneOffset.UTC);
            java.time.LocalDateTime windowStart = itemStart.minusMinutes(5);
            java.time.LocalDateTime windowEnd = itemStart.plusMinutes(5);
            java.util.List<Activity> nearby = activityRepository
                .findByUser_IdAndStartDateBetween(uid, windowStart, windowEnd);
            for (Activity candidate : nearby) {
                if (candidate.getImportSource().equals(source)) continue; // same source, different external id is fine
                // Distance match: within 5%
                if (item.getDistanceMeters() != null && candidate.getDistanceMeters() != null) {
                    double ratio = item.getDistanceMeters().doubleValue() /
                                   candidate.getDistanceMeters().doubleValue();
                    if (ratio >= 0.95 && ratio <= 1.05) {
                        // Duplicate from different source — skip
                        logger.info("Skipping " + source + " activity (duplicate of " +
                            candidate.getImportSource() + " activity " + candidate.getId() + ")");
                        throw new IllegalStateException("CROSS_SOURCE_DUPLICATE");
                    }
                }
            }
        }

        if (activity == null) activity = new Activity();
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
        if (item.getElevationGainFt() != null) {
            activity.setElevationGainFt(item.getElevationGainFt().setScale(2, RoundingMode.HALF_UP));
        }
        if (item.getAvgHeartRateBpm() != null) {
            activity.setAvgHeartRateBpm(item.getAvgHeartRateBpm());
        }
        if (item.getMaxHeartRateBpm() != null) {
            activity.setMaxHeartRateBpm(item.getMaxHeartRateBpm());
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

    /**
     * Removes duplicate activities for a user across import sources.
     * When a Strava and HealthKit activity cover the same run (same time ±5min, same distance ±5%),
     * the non-Strava (lower-priority) one is deleted.
     * Priority: strava > apple_health > health_connect
     */
    @org.springframework.transaction.annotation.Transactional
    public int deduplicateActivities(java.util.UUID userId) {
        java.util.List<Activity> all = activityRepository.findByUser_Id(userId);
        java.util.Set<java.util.UUID> toDelete = new java.util.HashSet<>();

        // Sort: strava first (it wins), then apple_health, then others
        all.sort((a, b) -> sourceRank(a.getImportSource()) - sourceRank(b.getImportSource()));

        for (int i = 0; i < all.size(); i++) {
            Activity a = all.get(i);
            if (toDelete.contains(a.getId())) continue;
            for (int j = i + 1; j < all.size(); j++) {
                Activity b = all.get(j);
                if (toDelete.contains(b.getId())) continue;
                if (a.getImportSource().equals(b.getImportSource())) continue; // same source, not a cross-source dupe
                if (a.getStartDate() == null || b.getStartDate() == null) continue;

                long diffMinutes = Math.abs(java.time.Duration.between(a.getStartDate(), b.getStartDate()).toMinutes());
                if (diffMinutes > 5) continue;

                if (a.getDistanceMeters() != null && b.getDistanceMeters() != null) {
                    double ratio = a.getDistanceMeters().doubleValue() / b.getDistanceMeters().doubleValue();
                    if (ratio >= 0.95 && ratio <= 1.05) {
                        // b is the lower-priority duplicate — mark for deletion
                        toDelete.add(b.getId());
                        logger.info("Dedup: removing " + b.getImportSource() + " activity " + b.getId()
                            + " (duplicate of " + a.getImportSource() + " activity " + a.getId() + ")");
                    }
                }
            }
        }

        for (java.util.UUID id : toDelete) {
            activityRepository.deleteById(id);
        }
        logger.info("Dedup complete for user " + userId + ": removed " + toDelete.size() + " duplicate(s)");
        return toDelete.size();
    }

    private static int sourceRank(String source) {
        return switch (source == null ? "" : source) {
            case "strava" -> 0;
            case "apple_health" -> 1;
            case "health_connect" -> 2;
            default -> 3;
        };
    }

}