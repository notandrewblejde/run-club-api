package com.runclub.api.service;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityKudoRepository;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final ActivityKudoRepository kudoRepository;

    public ActivityService(ActivityRepository activityRepository,
                           UserRepository userRepository,
                           ActivityKudoRepository kudoRepository) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.kudoRepository = kudoRepository;
    }

    public Page<Activity> getUserActivities(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return activityRepository.findByUserOrderByStartDateDesc(user, pageable);
    }

    public Map<String, Object> getActivityDetail(UUID activityId, UUID requesterId) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new RuntimeException("Activity not found"));

        userRepository.findById(requesterId)
            .orElseThrow(() -> new RuntimeException("Requester not found"));

        // Activities are visible to any authenticated user for now. Privacy levels
        // (public / followers-only / club-only) can layer on later.
        Map<String, Object> response = buildActivityDetailResponse(activity);
        response.put("kudoed", kudoRepository.findByActivityAndUser(
            activity,
            userRepository.findById(requesterId).get()
        ).isPresent());
        response.put("is_owner", activity.getUser().getId().equals(requesterId));
        return response;
    }

    public Map<String, Object> buildActivityCard(Activity activity) {
        return buildActivityDetailResponse(activity);
    }

    private Map<String, Object> buildActivityDetailResponse(Activity activity) {
        User owner = activity.getUser();
        Map<String, Object> response = new HashMap<>();

        response.put("id", activity.getId());
        response.put("strava_id", activity.getStravaActivityId());
        response.put("user_id", owner.getId());

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", owner.getId());
        userMap.put("name", owner.getDisplayName());
        userMap.put("avatar_url", owner.getProfilePicUrl());
        response.put("user", userMap);

        response.put("name", activity.getName());
        response.put("sport_type", activity.getType());
        response.put("start_date", activity.getStartDate());
        response.put("city", activity.getCity());
        response.put("state", activity.getState());

        response.put("distance_meters", activity.getDistanceMeters());
        response.put("distance_miles", activity.getDistanceMiles());
        response.put("moving_time_secs", activity.getMovingTimeSeconds());
        response.put("elapsed_time_secs", activity.getElapsedTimeSeconds());

        if (activity.getMovingTimeSeconds() != null && activity.getDistanceMeters() != null) {
            BigDecimal miles = activity.getDistanceMiles();
            if (miles != null && miles.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal secsPerMile = new BigDecimal(activity.getMovingTimeSeconds())
                    .divide(miles, 0, java.math.RoundingMode.HALF_UP);
                response.put("avg_pace_secs_per_mile", secsPerMile.longValue());
            }
        }

        response.put("avg_pace_display", activity.getAvgPaceDisplay());
        response.put("elevation_gain_ft", activity.getElevationGainFt());
        response.put("max_elevation_ft", activity.getMaxElevationFt());
        response.put("avg_heart_rate_bpm", activity.getAvgHeartRateBpm());
        response.put("max_heart_rate_bpm", activity.getMaxHeartRateBpm());
        response.put("map_polyline", activity.getMapPolyline());
        response.put("photos", activity.getPhotos());
        response.put("kudos_count", activity.getKudosCount());
        response.put("comment_count", activity.getCommentCount());
        response.put("is_personal_record", activity.getIsPersonalRecord());

        return response;
    }

    public Activity upsertActivity(Activity activity) {
        Activity existing = activityRepository.findByStravaActivityId(activity.getStravaActivityId())
            .orElse(null);

        if (existing != null) {
            existing.setName(activity.getName());
            existing.setType(activity.getType());
            existing.setDistanceMeters(activity.getDistanceMeters());
            existing.setMovingTimeSeconds(activity.getMovingTimeSeconds());
            existing.setElapsedTimeSeconds(activity.getElapsedTimeSeconds());
            existing.setElevationGainFt(activity.getElevationGainFt());
            existing.setMaxElevationFt(activity.getMaxElevationFt());
            existing.setAvgHeartRateBpm(activity.getAvgHeartRateBpm());
            existing.setMaxHeartRateBpm(activity.getMaxHeartRateBpm());
            existing.setStartDate(activity.getStartDate());
            existing.setCity(activity.getCity());
            existing.setState(activity.getState());
            existing.setMapPolyline(activity.getMapPolyline());
            existing.setPhotos(activity.getPhotos());
            existing.setIsPersonalRecord(activity.getIsPersonalRecord());
            existing.setKudosCount(activity.getKudosCount());
            existing.setCommentCount(activity.getCommentCount());
            return activityRepository.save(existing);
        }

        return activityRepository.save(activity);
    }
}
