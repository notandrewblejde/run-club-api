package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.dto.UpdateActivityRequest;
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
    private final FollowService followService;

    public ActivityService(ActivityRepository activityRepository,
                           UserRepository userRepository,
                           ActivityKudoRepository kudoRepository,
                           FollowService followService) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.kudoRepository = kudoRepository;
        this.followService = followService;
    }

    public Page<com.runclub.api.entity.Activity> getUserActivities(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return activityRepository.findByUserOrderByStartDateDesc(user, pageable);
    }

    /**
     * Detail view of one activity. Enforces privacy: a private profile's
     * activities are only visible to the owner or accepted followers.
     */
    public Activity getActivity(UUID activityId, UUID requesterId) {
        com.runclub.api.entity.Activity entity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("activity"));

        User requester = userRepository.findById(requesterId)
            .orElseThrow(() -> ApiException.notFound("user"));

        User owner = entity.getUser();
        boolean isOwner = owner.getId().equals(requesterId);
        boolean isPrivate = "private".equals(owner.getPrivacyLevel());
        if (isPrivate && !isOwner && !followService.isAcceptedFollower(requesterId, owner.getId())) {
            throw ApiException.forbidden("This activity is private");
        }

        Activity dto = Activity.from(entity);
        dto.kudoedByViewer = kudoRepository.findByActivityAndUser(entity, requester).isPresent();
        dto.ownedByViewer = isOwner;
        return dto;
    }

    /**
     * Updates app-owned overlay fields (note, app photos). Only the activity owner may call.
     * Strava sync never touches these columns.
     */
    public Activity updateActivityDetails(UUID activityId, UUID requesterId, UpdateActivityRequest body) {
        com.runclub.api.entity.Activity entity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("activity"));
        if (!entity.getUser().getId().equals(requesterId)) {
            throw ApiException.forbidden("Only the activity owner can edit details");
        }

        if (body.userNote == null && body.appPhotos == null) {
            throw ApiException.badRequest("Provide user_note and/or app_photos");
        }

        if (body.userNote != null) {
            String trimmed = body.userNote.trim();
            entity.setUserNote(trimmed.isEmpty() ? null : trimmed);
        }
        if (body.appPhotos != null) {
            entity.setAppPhotos(body.appPhotos);
        }

        com.runclub.api.entity.Activity saved = activityRepository.save(entity);
        return getActivity(saved.getId(), requesterId);
    }
}
