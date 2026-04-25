package com.runclub.api.service;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public ActivityService(ActivityRepository activityRepository, UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
    }

    public Page<Activity> getUserActivities(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page - 1, Math.min(limit, 100)); // Max 100 per page
        return activityRepository.findByUserOrderByStartDateDesc(user, pageable);
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
