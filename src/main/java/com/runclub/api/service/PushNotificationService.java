package com.runclub.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runclub.api.entity.UserNotificationPrefs;
import com.runclub.api.entity.UserPushToken;
import com.runclub.api.repository.UserNotificationPrefsRepository;
import com.runclub.api.repository.UserPushTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sends push notifications via Expo Push Notification service.
 * Handles debounce, user preferences, and club-member fan-out.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final Duration DEBOUNCE_MINUTES = Duration.ofMinutes(1);

    private final UserPushTokenRepository tokenRepo;
    private final UserNotificationPrefsRepository prefsRepo;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public PushNotificationService(UserPushTokenRepository tokenRepo,
                                   UserNotificationPrefsRepository prefsRepo,
                                   JdbcTemplate jdbc) {
        this.tokenRepo = tokenRepo;
        this.prefsRepo = prefsRepo;
        this.jdbc = jdbc;
    }

    // ── Device token management ──────────────────────────────────────────────

    @Transactional
    public void registerToken(UUID userId, String token, String platform) {
        // Upsert — update timestamp if already exists
        jdbc.update("""
            INSERT INTO user_push_tokens (user_id, token, platform, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, token) DO UPDATE SET updated_at = EXCLUDED.updated_at
            """, userId, token, platform != null ? platform : "expo", Instant.now());
        log.info("Push token registered: user={} platform={}", userId, platform);
    }

    @Transactional
    public void removeToken(UUID userId, String token) {
        tokenRepo.deleteByUserIdAndToken(userId, token);
    }

    // ── Notification preferences ─────────────────────────────────────────────

    public UserNotificationPrefs getOrCreatePrefs(UUID userId) {
        return prefsRepo.findById(userId).orElseGet(() -> {
            UserNotificationPrefs p = new UserNotificationPrefs();
            p.setUserId(userId);
            return prefsRepo.save(p);
        });
    }

    @Transactional
    public UserNotificationPrefs updatePrefs(UUID userId, Boolean clubActivity, Boolean dailyCoach, Boolean goalProgress) {
        UserNotificationPrefs p = getOrCreatePrefs(userId);
        if (clubActivity != null) p.setClubActivityAlerts(clubActivity);
        if (dailyCoach != null) p.setDailyCoachTip(dailyCoach);
        if (goalProgress != null) p.setGoalProgress(goalProgress);
        p.setUpdatedAt(Instant.now());
        return prefsRepo.save(p);
    }

    // ── Send notifications ───────────────────────────────────────────────────

    /**
     * Notify club members when someone logs an activity.
     * Debounces per (recipient, notification_type) to prevent import storms.
     */
    @Async
    public void notifyClubMembersActivityAsync(UUID actorUserId, String actorName,
                                               UUID activityId, String activityName,
                                               double distanceMiles,
                                               List<UUID> clubMemberIds) {
        if (clubMemberIds == null || clubMemberIds.isEmpty()) return;

        String debounceKey = "CLUB_ACTIVITY_" + actorUserId;
        String title = actorName + " just ran!";
        String body = String.format("%s · %.1f mi", activityName, distanceMiles);
        Map<String, Object> payload = Map.of("type", "CLUB_ACTIVITY",
            "activityId", activityId.toString(), "actorId", actorUserId.toString());

        for (UUID recipientId : clubMemberIds) {
            if (recipientId.equals(actorUserId)) continue; // don't notify yourself
            try {
                // Check user pref
                UserNotificationPrefs prefs = getOrCreatePrefs(recipientId);
                if (!prefs.isClubActivityAlerts()) continue;

                // Debounce: one notification per actor per minute per recipient
                String recipientDebounceKey = debounceKey + "_TO_" + recipientId;
                if (!checkAndSetDebounce(recipientId, recipientDebounceKey)) continue;

                sendToUser(recipientId, title, body, payload);
            } catch (Exception e) {
                log.warn("Failed to notify member {} of activity: {}", recipientId, e.getMessage());
            }
        }
    }

    /**
     * Send daily 8am ET AI coach tip to all users who opted in.
     * Called by a scheduled job.
     */
    @Async
    public void sendDailyCoachTipsAsync(List<UUID> userIds, Map<UUID, String> userCoachTips) {
        for (UUID userId : userIds) {
            try {
                UserNotificationPrefs prefs = getOrCreatePrefs(userId);
                if (!prefs.isDailyCoachTip()) continue;

                String tip = userCoachTips.getOrDefault(userId, null);
                if (tip == null || tip.isBlank()) continue;

                // Trim to ~100 chars for notification body
                String shortTip = tip.length() > 100 ? tip.substring(0, 97) + "..." : tip;
                sendToUser(userId, "Your coach recommends today", shortTip,
                    Map.of("type", "DAILY_COACH_TIP", "screen", "ai_coach"));
            } catch (Exception e) {
                log.warn("Failed daily coach tip for user {}: {}", userId, e.getMessage());
            }
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void sendToUser(UUID userId, String title, String body, Map<String, Object> data) {
        List<UserPushToken> tokens = tokenRepo.findByUserId(userId);
        if (tokens.isEmpty()) return;

        List<Map<String, Object>> messages = tokens.stream().map(t -> {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("to", t.getToken());
            msg.put("title", title);
            msg.put("body", body);
            msg.put("data", data);
            msg.put("sound", "default");
            msg.put("priority", "normal");
            return msg;
        }).collect(Collectors.toList());

        sendToExpo(messages);
    }

    private void sendToExpo(List<Map<String, Object>> messages) {
        try {
            String json = mapper.writeValueAsString(messages);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(EXPO_PUSH_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                log.debug("Expo push sent {} messages", messages.size());
            } else {
                log.warn("Expo push returned {}: {}", resp.statusCode(), resp.body().substring(0, Math.min(200, resp.body().length())));
            }
        } catch (Exception e) {
            log.warn("Expo push failed: {}", e.getMessage());
        }
    }

    /**
     * Debounce check using DB. Returns true if we should send (not debounced).
     * Atomic upsert ensures only one notification fires per window.
     */
    private boolean checkAndSetDebounce(UUID userId, String key) {
        try {
            int rows = jdbc.update("""
                INSERT INTO push_debounce (user_id, notification_type, last_sent_at)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, notification_type) DO UPDATE
                SET last_sent_at = EXCLUDED.last_sent_at
                WHERE push_debounce.last_sent_at < ?
                RETURNING user_id
                """,
                userId, key, Instant.now(),
                Instant.now().minus(DEBOUNCE_MINUTES));
            return rows > 0;
        } catch (Exception e) {
            // If debounce check fails, allow the notification
            return true;
        }
    }
}
