package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.User;
import com.runclub.api.model.Activity;
import com.runclub.api.repository.ActivityKudoRepository;
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
    private final ActivityKudoRepository kudoRepository;

    public ActivityService(ActivityRepository activityRepository,
                           UserRepository userRepository,
                           ActivityKudoRepository kudoRepository) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.kudoRepository = kudoRepository;
    }

    public Page<com.runclub.api.entity.Activity> getUserActivities(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return activityRepository.findByUserOrderByStartDateDesc(user, pageable);
    }

    /**
     * Detailed view of a single activity. Returns the resource model with viewer-aware
     * fields (kudoed_by_viewer, owned_by_viewer) populated.
     */
    public Activity getActivity(UUID activityId, UUID requesterId) {
        com.runclub.api.entity.Activity entity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("activity"));

        User requester = userRepository.findById(requesterId)
            .orElseThrow(() -> ApiException.notFound("user"));

        // Activities are visible to any authenticated user for now. Privacy levels
        // (public / followers-only / club-only) can layer on later.
        Activity dto = Activity.from(entity);
        dto.kudoedByViewer = kudoRepository.findByActivityAndUser(entity, requester).isPresent();
        dto.ownedByViewer = entity.getUser().getId().equals(requesterId);
        return dto;
    }
}
