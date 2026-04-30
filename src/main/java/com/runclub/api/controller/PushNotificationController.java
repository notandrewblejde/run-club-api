package com.runclub.api.controller;

import com.runclub.api.api.Auth;
import com.runclub.api.entity.UserNotificationPrefs;
import com.runclub.api.service.PushNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/me/push")
public class PushNotificationController {

    private final PushNotificationService pushService;

    public PushNotificationController(PushNotificationService pushService) {
        this.pushService = pushService;
    }

    /** Register or update a push token for this device. */
    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> registerToken(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        String token = body.get("token");
        String platform = body.getOrDefault("platform", "expo");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "token is required"));
        }
        pushService.registerToken(userId, token, platform);
        return ResponseEntity.ok(Map.of("status", "registered"));
    }

    /** Remove a push token (e.g. on logout). */
    @DeleteMapping("/token")
    public ResponseEntity<Void> removeToken(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        String token = body.get("token");
        if (token != null) pushService.removeToken(userId, token);
        return ResponseEntity.noContent().build();
    }

    /** Get notification preferences. */
    @GetMapping("/prefs")
    public ResponseEntity<UserNotificationPrefs> getPrefs(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        return ResponseEntity.ok(pushService.getOrCreatePrefs(userId));
    }

    /** Update notification preferences. */
    @PatchMapping("/prefs")
    public ResponseEntity<UserNotificationPrefs> updatePrefs(
            @RequestBody Map<String, Boolean> body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        UserNotificationPrefs updated = pushService.updatePrefs(userId,
            body.get("club_activity_alerts"),
            body.get("daily_coach_tip"),
            body.get("goal_progress"),
            body.get("activity_comment_alerts"));
        return ResponseEntity.ok(updated);
    }
}
