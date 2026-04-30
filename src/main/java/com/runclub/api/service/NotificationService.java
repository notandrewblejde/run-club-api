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
    private static final int COMMENT_BODY_MAX = 220;

    private final UserNotificationRepository notificationRepository;
    private final ActivityRepository activityRepository;
    private final AthleteIntelligenceService athleteIntelligenceService;
    private final ObjectMapper objectMapper;
    private final PushNotificationService pushNotificationService;

    public NotificationService(UserNotificationRepository notificationRepository,
                               ActivityRepository activityRepository,
                               AthleteIntelligenceService athleteIntelligenceService,
                               ObjectMapper objectMapper,
                               PushNotificationService pushNotificationService) {
        this.notificationRepository = notificationRepository;
        this.activityRepository = activityRepository;
        this.athleteIntelligenceService = athleteIntelligenceService;
        this.objectMapper = objectMapper;
        this.pushNotificationService = pushNotificationService;
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

    /**
     * In-app notification + optional push when another user comments on {@code ownerUserId}'s activity.
     * Each comment creates its own row (not idempotent per activity).
     */
    @Transactional
    public void createActivityCommentNotification(UUID ownerUserId, UUID activityId, UUID commentId,
                                                  String actorDisplayName, String commentContent) {
        UserNotification row = new UserNotification();
        row.setUserId(ownerUserId);
        row.setType(UserNotification.TYPE_ACTIVITY_COMMENT);
        String name = actorDisplayName != null && !actorDisplayName.isBlank() ? actorDisplayName.trim() : "Someone";
        row.setTitle(name + " commented on your activity");
        row.setBody(truncateForNotification(commentContent));
        row.setRelatedActivityId(activityId);
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("activityId", activityId.toString());
            payload.put("commentId", commentId.toString());
            row.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            row.setPayloadJson("{\"activityId\":\"" + activityId + "\",\"commentId\":\"" + commentId + "\"}");
        }
        row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        notificationRepository.save(row);

        try {
            pushNotificationService.sendActivityCommentPush(
                ownerUserId, row.getTitle(), row.getBody(), activityId, commentId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Push for activity comment failed: " + e.getMessage());
        }
    }

    private static String truncateForNotification(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() <= COMMENT_BODY_MAX) return s;
        return s.substring(0, COMMENT_BODY_MAX - 1) + "…";
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
