package com.runclub.api.service;

import com.runclub.api.dto.StravaActivityResponse;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pulls activities from Strava and writes them into our DB. Used by:
 *   - one-time backfill after a fresh OAuth connection
 *   - webhook-triggered single-activity sync on create/update
 *   - webhook-triggered delete
 *
 * Treated as runs-only for now: we filter on sport_type / type to avoid persisting
 * rides, swims, etc. unless explicitly desired later.
 */
@Service
public class StravaActivitySyncService {
    private static final Logger logger = Logger.getLogger(StravaActivitySyncService.class.getName());

    private static final BigDecimal METERS_TO_FEET = new BigDecimal("3.28084");
    private static final int BACKFILL_PER_PAGE = 50;
    private static final int BACKFILL_MAX_PAGES = 4; // ~200 most recent activities

    private final StravaApiService stravaApiService;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final GoalAttributionService goalAttributionService;

    public StravaActivitySyncService(StravaApiService stravaApiService,
                                     ActivityRepository activityRepository,
                                     UserRepository userRepository,
                                     GoalAttributionService goalAttributionService) {
        this.stravaApiService = stravaApiService;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.goalAttributionService = goalAttributionService;
    }

    /**
     * Backfills the most recent activities for a freshly connected user.
     * Runs asynchronously so the OAuth callback can return quickly.
     */
    @Async
    public void backfillRecentActivitiesAsync(UUID userId) {
        try {
            backfillRecentActivities(userId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Backfill failed for user " + userId, e);
        }
    }

    public int backfillRecentActivities(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getStravaAccessToken() == null) {
            logger.warning("Skipping backfill: user " + userId + " has no Strava token");
            return 0;
        }

        int total = 0;
        for (int page = 1; page <= BACKFILL_MAX_PAGES; page++) {
            List<StravaActivityResponse> batch = stravaApiService.listAthleteActivities(user, null, page, BACKFILL_PER_PAGE);
            if (batch.isEmpty()) break;
            for (StravaActivityResponse stravaActivity : batch) {
                if (!isRunningActivity(stravaActivity)) continue;
                try {
                    upsertFromStrava(user, stravaActivity);
                    total++;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to upsert activity " + stravaActivity.getId(), e);
                }
            }
            if (batch.size() < BACKFILL_PER_PAGE) break;
        }
        logger.info("Backfill complete for user " + userId + ": " + total + " activities");
        return total;
    }

    /**
     * Webhook entry point: fetch the activity by id and upsert. Caller is the
     * webhook controller; runs async so we acknowledge Strava within 2s.
     */
    @Async
    public void handleActivityCreatedOrUpdatedAsync(long stravaAthleteId, long activityId) {
        try {
            Optional<User> userOpt = userRepository.findByStravaAthleteId(stravaAthleteId);
            if (userOpt.isEmpty()) {
                logger.warning("Webhook for unknown athlete " + stravaAthleteId + ", ignoring");
                return;
            }
            User user = userOpt.get();
            StravaActivityResponse activity = stravaApiService.fetchActivity(user, activityId);
            if (activity == null) {
                logger.warning("Strava returned no body for activity " + activityId);
                return;
            }
            if (!isRunningActivity(activity)) {
                logger.fine("Ignoring non-running activity " + activityId);
                return;
            }
            upsertFromStrava(user, activity);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed handling webhook activity " + activityId, e);
        }
    }

    @Async
    public void handleActivityDeletedAsync(long activityId) {
        activityRepository.findByStravaActivityId(activityId).ifPresent(activityRepository::delete);
    }

    private boolean isRunningActivity(StravaActivityResponse a) {
        String sport = a.getSportType() != null ? a.getSportType() : a.getType();
        if (sport == null) return false;
        sport = sport.toLowerCase();
        return sport.contains("run") || sport.equals("trailrun") || sport.equals("virtualrun");
    }

    private Activity upsertFromStrava(User user, StravaActivityResponse src) {
        Activity activity = activityRepository.findByStravaActivityId(src.getId())
            .orElseGet(Activity::new);

        activity.setUser(user);
        activity.setStravaActivityId(src.getId());
        activity.setName(src.getName());
        activity.setType(src.getSportType() != null ? src.getSportType() : src.getType());

        if (src.getDistance() != null) {
            activity.setDistanceMeters(BigDecimal.valueOf(src.getDistance()).setScale(2, RoundingMode.HALF_UP));
        }
        activity.setMovingTimeSeconds(src.getMovingTime());
        activity.setElapsedTimeSeconds(src.getElapsedTime());
        if (src.getTotalElevationGain() != null) {
            activity.setElevationGainFt(BigDecimal.valueOf(src.getTotalElevationGain())
                .multiply(METERS_TO_FEET).setScale(2, RoundingMode.HALF_UP));
        }
        if (src.getElevHigh() != null) {
            activity.setMaxElevationFt(BigDecimal.valueOf(src.getElevHigh())
                .multiply(METERS_TO_FEET).setScale(2, RoundingMode.HALF_UP));
        }
        if (src.getAverageHeartrate() != null) {
            activity.setAvgHeartRateBpm(src.getAverageHeartrate().intValue());
        }
        if (src.getMaxHeartrate() != null) {
            activity.setMaxHeartRateBpm(src.getMaxHeartrate().intValue());
        }
        if (src.getStartDate() != null) {
            activity.setStartDate(src.getStartDate().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        }
        activity.setCity(src.getLocationCity());
        activity.setState(src.getLocationState());

        if (src.getMap() != null) {
            String poly = src.getMap().getPolyline() != null ? src.getMap().getPolyline() : src.getMap().getSummaryPolyline();
            activity.setMapPolyline(poly);
        }

        // Strava-sourced gallery only; user_note / app_photos are never set here (JPA keeps existing values).
        if (src.getPhotos() != null && !src.getPhotos().isEmpty()) {
            String[] urls = src.getPhotos().stream()
                .filter(p -> p.getUrls() != null && p.getUrls().getFull() != null)
                .map(p -> p.getUrls().getFull())
                .toArray(String[]::new);
            if (urls.length > 0) activity.setPhotos(urls);
        }

        if (src.getKudosCount() != null) activity.setKudosCount(src.getKudosCount());
        if (src.getCommentCount() != null) activity.setCommentCount(src.getCommentCount());
        activity.setIsPersonalRecord(src.getPrCount() != null && src.getPrCount() > 0);

        if (activity.getCreatedAt() == null) {
            activity.setCreatedAt(LocalDateTime.now());
        }

        Activity saved = activityRepository.save(activity);
        // Best-effort: attribute to any active club goals this user belongs to.
        // Failures are logged inside the attribution service and don't fail the sync.
        goalAttributionService.attributeToActiveGoals(saved);
        return saved;
    }
}
