package com.runclub.api.service;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregations and read-mostly views over the User entity.
 */
@Service
public class UserProfileService {
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final FollowService followService;

    public UserProfileService(UserRepository userRepository,
                              ActivityRepository activityRepository,
                              FollowService followService) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.followService = followService;
    }

    public Map<String, Object> getProfile(UUID userId, UUID requesterId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> stats = computeStats(user);

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("name", user.getDisplayName());
        response.put("avatar_url", user.getProfilePicUrl());
        response.put("bio", null);
        response.put("city", null);
        response.put("state", null);
        response.put("strava_connected", user.getStravaAthleteId() != null);
        response.put("followers_count", followService.getFollowersCount(user.getId()));
        response.put("following_count", followService.getFollowingCount(user.getId()));
        response.put("is_self", requesterId != null && requesterId.equals(user.getId()));
        if (requesterId != null && !requesterId.equals(user.getId())) {
            response.put("is_following", followService.isFollowing(requesterId, user.getId()));
        }
        response.put("stats", stats);
        return response;
    }

    private Map<String, Object> computeStats(User user) {
        Pageable allTime = PageRequest.of(0, 1000);
        Page<Activity> recent = activityRepository.findByUserOrderByStartDateDesc(user, allTime);
        long totalActivities = recent.getTotalElements();

        BigDecimal totalMiles = BigDecimal.ZERO;
        long totalSeconds = 0;
        BigDecimal totalElevationFt = BigDecimal.ZERO;

        BigDecimal milesLast30 = BigDecimal.ZERO;
        long activitiesLast30 = 0;

        LocalDateTime cutoff30 = LocalDateTime.now().minusDays(30);

        for (Activity a : recent.getContent()) {
            if (a.getDistanceMiles() != null) totalMiles = totalMiles.add(a.getDistanceMiles());
            if (a.getMovingTimeSeconds() != null) totalSeconds += a.getMovingTimeSeconds();
            if (a.getElevationGainFt() != null) totalElevationFt = totalElevationFt.add(a.getElevationGainFt());

            if (a.getStartDate() != null && a.getStartDate().isAfter(cutoff30)) {
                if (a.getDistanceMiles() != null) milesLast30 = milesLast30.add(a.getDistanceMiles());
                activitiesLast30++;
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_activities", totalActivities);
        stats.put("total_miles", totalMiles.setScale(2, RoundingMode.HALF_UP));
        stats.put("total_moving_seconds", totalSeconds);
        stats.put("total_elevation_ft", totalElevationFt.setScale(0, RoundingMode.HALF_UP));
        stats.put("miles_last_30d", milesLast30.setScale(2, RoundingMode.HALF_UP));
        stats.put("activities_last_30d", activitiesLast30);
        return stats;
    }
}
