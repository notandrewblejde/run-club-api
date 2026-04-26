package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.model.UserProfile;
import com.runclub.api.model.UserStats;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

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

    public UserProfile getProfile(UUID userId, UUID requesterId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));

        UserProfile p = new UserProfile();
        p.id = user.getId();
        p.name = user.getDisplayName();
        p.avatarUrl = user.getProfilePicUrl();
        p.bio = user.getBio();
        p.city = user.getCity();
        p.state = user.getState();
        p.privacyLevel = user.getPrivacyLevel();
        p.stravaConnected = user.getStravaAthleteId() != null;
        p.followersCount = followService.getFollowersCount(user.getId());
        p.followingCount = followService.getFollowingCount(user.getId());
        p.isSelf = requesterId != null && requesterId.equals(user.getId());
        p.followStatus = followService.getFollowStatus(requesterId, user.getId());

        // Stats are aggregate so they're fine to expose. Activity lists are gated
        // separately at the activity endpoints.
        p.stats = computeStats(user);
        return p;
    }

    private UserStats computeStats(User user) {
        Pageable allTime = PageRequest.of(0, 1000);
        Page<Activity> recent = activityRepository.findByUserOrderByStartDateDesc(user, allTime);

        BigDecimal totalMiles = BigDecimal.ZERO;
        long totalSeconds = 0;
        BigDecimal totalElevationFt = BigDecimal.ZERO;
        BigDecimal milesLast30 = BigDecimal.ZERO;
        long activitiesLast30 = 0;
        long movingSecondsLast30 = 0;

        LocalDateTime cutoff30 = LocalDateTime.now().minusDays(30);

        for (Activity a : recent.getContent()) {
            if (a.getDistanceMiles() != null) totalMiles = totalMiles.add(a.getDistanceMiles());
            if (a.getMovingTimeSeconds() != null) totalSeconds += a.getMovingTimeSeconds();
            if (a.getElevationGainFt() != null) totalElevationFt = totalElevationFt.add(a.getElevationGainFt());

            if (a.getStartDate() != null && a.getStartDate().isAfter(cutoff30)) {
                if (a.getDistanceMiles() != null) milesLast30 = milesLast30.add(a.getDistanceMiles());
                activitiesLast30++;
                if (a.getMovingTimeSeconds() != null) {
                    movingSecondsLast30 += a.getMovingTimeSeconds();
                }
            }
        }

        UserStats s = new UserStats();
        s.totalActivities = recent.getTotalElements();
        s.totalDistanceMiles = totalMiles.setScale(2, RoundingMode.HALF_UP).doubleValue();
        s.totalMovingSeconds = totalSeconds;
        s.totalElevationFt = totalElevationFt.setScale(0, RoundingMode.HALF_UP).doubleValue();
        s.distanceMiles30d = milesLast30.setScale(2, RoundingMode.HALF_UP).doubleValue();
        s.activities30d = activitiesLast30;
        s.movingSeconds30d = movingSecondsLast30;
        return s;
    }
}
