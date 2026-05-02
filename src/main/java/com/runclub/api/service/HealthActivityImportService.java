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
    private final com.runclub.api.repository.UserRepository userRepository;
    private final GoalAttributionService goalAttributionService;
    private final AthleteIntelligenceService athleteIntelligenceService;
    private final TrainingGoalService trainingGoalService;
    private final NotificationService notificationService;

    public HealthActivityImportService(ActivityRepository activityRepository,
                                       GoalAttributionService goalAttributionService,
                                       AthleteIntelligenceService athleteIntelligenceService,
                                       TrainingGoalService trainingGoalService,
                                       NotificationService notificationService,
                                       com.runclub.api.repository.UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.goalAttributionService = goalAttributionService;
        this.athleteIntelligenceService = athleteIntelligenceService;
        this.trainingGoalService = trainingGoalService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
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
                boolean isDistanceDupe = false;
                if (item.getDistanceMeters() != null && candidate.getDistanceMeters() != null) {
                    double ratio = item.getDistanceMeters().doubleValue() /
                                   candidate.getDistanceMeters().doubleValue();
                    isDistanceDupe = (ratio >= 0.95 && ratio <= 1.05);
                } else if (item.getMovingTimeSeconds() != null && candidate.getMovingTimeSeconds() != null) {
                    double durationRatio = item.getMovingTimeSeconds().doubleValue() / candidate.getMovingTimeSeconds();
                    isDistanceDupe = (durationRatio >= 0.95 && durationRatio <= 1.05);
                } else {
                    // No distance or duration — time window match alone is sufficient
                    isDistanceDupe = true;
                }
                if (isDistanceDupe) {
                    // Duplicate from different source — skip
                    logger.info("Skipping " + source + " activity (duplicate of " +
                        candidate.getImportSource() + " activity " + candidate.getId() + ")");
                    throw new IllegalStateException("CROSS_SOURCE_DUPLICATE");
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

        // Sort: lower source rank = higher priority = kept
        // Tiebreaker: prefer activity with GPS/polyline data
        all.sort((a, b) -> {
            int rankDiff = sourceRank(a.getImportSource()) - sourceRank(b.getImportSource());
            if (rankDiff != 0) return rankDiff;
            // Same source rank: prefer the one with photos, then GPS data, then most recent
            boolean aHasPhotos = a.getAppPhotos() != null && a.getAppPhotos().length > 0;
            boolean bHasPhotos = b.getAppPhotos() != null && b.getAppPhotos().length > 0;
            if (aHasPhotos && !bHasPhotos) return -1;
            if (!aHasPhotos && bHasPhotos) return 1;
            boolean aHasGps = hasGpsData(a);
            boolean bHasGps = hasGpsData(b);
            if (aHasGps && !bHasGps) return -1;
            if (!aHasGps && bHasGps) return 1;
            return 0;
        });

        for (int i = 0; i < all.size(); i++) {
            Activity a = all.get(i);
            if (toDelete.contains(a.getId())) continue;
            for (int j = i + 1; j < all.size(); j++) {
                Activity b = all.get(j);
                if (toDelete.contains(b.getId())) continue;
                if (a.getStartDate() == null || b.getStartDate() == null) continue;

                long diffSeconds = Math.abs(java.time.Duration.between(a.getStartDate(), b.getStartDate()).toSeconds());
                if (diffSeconds > 300) continue; // more than 5 min apart — not the same run

                boolean sameSource = a.getImportSource().equals(b.getImportSource());
                boolean distanceMatch;
                if (a.getDistanceMeters() != null && b.getDistanceMeters() != null) {
                    double ratio = a.getDistanceMeters().doubleValue() / b.getDistanceMeters().doubleValue();
                    distanceMatch = (ratio >= 0.95 && ratio <= 1.05);
                } else {
                    // If either is missing distance, also check duration similarity
                    if (a.getMovingTimeSeconds() != null && b.getMovingTimeSeconds() != null) {
                        double durationRatio = (double) a.getMovingTimeSeconds() / b.getMovingTimeSeconds();
                        distanceMatch = (durationRatio >= 0.95 && durationRatio <= 1.05);
                    } else {
                        // Only time window match — treat as duplicate if within 2 min
                        distanceMatch = diffSeconds <= 120;
                    }
                }

                if (distanceMatch) {
                    // b is the lower-priority duplicate — mark for deletion
                    // For same-source: keep the one with GPS data, or the older one
                    toDelete.add(b.getId());
                    logger.info("Dedup: removing " + b.getImportSource() + " activity " + b.getId()
                        + " (duplicate of " + a.getImportSource() + " activity " + a.getId()
                        + (sameSource ? " [same-source]" : " [cross-source]") + ")");
                }
            }
        }

        for (java.util.UUID id : toDelete) {
            try {
                // Delete goal contributions first (FK constraint)
                goalAttributionService.deleteContributionsByActivity(id);
                activityRepository.deleteById(id);
            } catch (Exception e) {
                logger.log(java.util.logging.Level.WARNING, "Could not delete duplicate activity " + id, e);
            }
        }
        logger.info("Dedup complete for user " + userId + ": removed " + toDelete.size() + " duplicate(s)");
        return toDelete.size();
    }

    /**
     * Lower rank = higher priority = kept in cross-source dedup.
     * Priority is based on data quality: Strava has GPS/polyline, Apple Health may not.
     * If user only has Apple Health (no Strava), their activities are never cross-source deduped.
     */
    private static int sourceRank(String source) {
        return switch (source == null ? "" : source) {
            case "strava" -> 0;          // Best: GPS polyline, pace, heart rate from watch
            case "apple_health" -> 1;    // Good: Apple Watch / iPhone GPS
            case "health_connect" -> 2;  // OK: Android Health Connect
            default -> 3;
        };
    }

    /**
     * When deduping, prefer the activity with GPS/polyline data if ranks are equal.
     * This handles e.g. two apple_health entries where one has a map and one doesn't.
     */
    private static boolean hasGpsData(Activity a) {
        return a.getMapPolyline() != null && !a.getMapPolyline().isBlank();
    }


    /**
     * ONE-TIME startup dedup — runs on application start to clean up existing cross-source duplicates.
     * After this runs once successfully, the ongoing dedup in upsertOne prevents new duplicates.
     * This method is safe to run multiple times (idempotent).
     */
    @jakarta.annotation.PostConstruct
    public void runStartupDedup() {
        logger.info("@PostConstruct runStartupDedup() registered — will run in 30s");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(30_000); // wait 30s for app to fully start
                logger.info("Running one-time startup activity dedup...");

                // Hardcoded cleanup for known duplicates that survived previous runs
                java.util.List<String> knownDuplicates = java.util.List.of(
                    "2323b912-2ff2-4771-ad50-5245945d3d41",  // apple_health dup of strava fdf8adc9
                    "5d3eade6-2707-45e2-96ed-f4d46d569cf1"   // apple_health dup
                );
                for (String dupId : knownDuplicates) {
                    try {
                        java.util.UUID id = java.util.UUID.fromString(dupId);
                        if (activityRepository.existsById(id)) {
                            goalAttributionService.deleteContributionsByActivity(id);
                            activityRepository.deleteById(id);
                            logger.info("Deleted known duplicate activity: " + dupId);
                        }
                    } catch (Exception e) {
                        logger.log(java.util.logging.Level.WARNING, "Could not delete known duplicate " + dupId, e);
                    }
                }

                java.util.List<com.runclub.api.entity.User> allUsers = userRepository.findAll();
                int totalRemoved = 0;
                for (com.runclub.api.entity.User user : allUsers) {
                    try {
                        int removed = deduplicateActivities(user.getId());
                        if (removed > 0) {
                            logger.info("Dedup: removed " + removed + " duplicate(s) for user " + user.getId());
                            totalRemoved += removed;
                        }
                    } catch (Exception e) {
                        logger.log(java.util.logging.Level.WARNING, "Dedup failed for user " + user.getId(), e);
                    }
                }
                logger.info("Startup dedup complete: removed " + totalRemoved + " total duplicate(s) across " + allUsers.size() + " users");
            } catch (Exception e) {
                logger.log(java.util.logging.Level.WARNING, "Startup dedup error", e);
            }
        });
    }

}