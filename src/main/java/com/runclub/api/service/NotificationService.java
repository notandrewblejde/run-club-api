package com.runclub.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runclub.api.api.ApiException;
import com.runclub.api.dto.ActivityArrivedCopy;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.UserNotification;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.runclub.api.model.JsonDtos.NotificationPreview;
import com.runclub.api.model.Notification;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class NotificationService {

    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());

    private final UserNotificationRepository notificationRepository;
    private final ActivityRepository activityRepository;
    private final AthleteIntelligenceService athleteIntelligenceService;
    private final ObjectMapper objectMapper;

    public NotificationService(UserNotificationRepository notificationRepository,
                               ActivityRepository activityRepository,
                               AthleteIntelligenceService athleteIntelligenceService,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.activityRepository = activityRepository;
        this.athleteIntelligenceService = athleteIntelligenceService;
        this.objectMapper = objectMapper;
    }

    public void createActivityArrivedNotificationAsync(UUID userId, UUID activityId) {
        try {
            createActivityArrivedNotification(userId, activityId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Activity notification failed for activity " + activityId, e);
        }
    }

    /**
     * Idempotent per (user, activity): updates title/body on repeated Strava webhooks.
     */
    @Transactional
    public void createActivityArrivedNotification(UUID userId, UUID activityId) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("activity"));
        if (!activity.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Activity does not belong to this user");
        }

        ActivityArrivedCopy copy = athleteIntelligenceService.activityArrivedNotificationCopy(activity);

        Optional<UserNotification> existing = notificationRepository.findByUserIdAndTypeAndRelatedActivityId(
            userId, UserNotification.TYPE_ACTIVITY_ARRIVED, activityId);

        UserNotification row = existing.orElseGet(UserNotification::new);
        row.setUserId(userId);
        row.setType(UserNotification.TYPE_ACTIVITY_ARRIVED);
        row.setTitle(copy.title());
        row.setBody(copy.body());
        row.setRelatedActivityId(activityId);
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("activityId", activityId.toString());
            row.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            row.setPayloadJson("{\"activityId\":\"" + activityId + "\"}");
        }
        if (existing.isEmpty()) {
            row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        notificationRepository.save(row);
    }

    @Transactional(readOnly = true)
    public Page<UserNotification> list(UUID userId, int page, int limit) {
        int p = Math.max(page, 1) - 1;
        int lim = Math.min(Math.max(limit, 1), 50);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(p, lim));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public NotificationPreview preview(UUID userId) {
        long unread = unreadCount(userId);
        Optional<UserNotification> firstUnread = notificationRepository
            .findFirstByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId);
        Optional<UserNotification> latestAny = notificationRepository
            .findFirstByUserIdOrderByCreatedAtDesc(userId);
        UserNotification latest = firstUnread.or(() -> latestAny).orElse(null);
        return NotificationPreview.builder()
            .unreadCount(unread)
            .latest(latest == null ? null : Notification.from(latest))
            .build();
    }

    @Transactional
    public UserNotification markRead(UUID userId, UUID notificationId) {
        UserNotification n = notificationRepository.findById(notificationId)
            .orElseThrow(() -> ApiException.notFound("notification"));
        if (!n.getUserId().equals(userId)) {
            throw ApiException.forbidden("Notification belongs to another user");
        }
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now(ZoneOffset.UTC));
            notificationRepository.save(n);
        }
        return n;
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllReadForUser(userId, LocalDateTime.now(ZoneOffset.UTC));
    }

}
