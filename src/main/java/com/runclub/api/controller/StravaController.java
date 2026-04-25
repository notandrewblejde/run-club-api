package com.runclub.api.controller;

import com.runclub.api.dto.StravaWebhookEvent;
import com.runclub.api.entity.User;
import com.runclub.api.repository.UserRepository;
import com.runclub.api.service.StravaActivitySyncService;
import com.runclub.api.service.StravaOAuthService;
import com.runclub.api.service.StravaWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/v1/strava")
public class StravaController {
    private static final Logger logger = Logger.getLogger(StravaController.class.getName());

    private final StravaOAuthService stravaOAuthService;
    private final StravaActivitySyncService stravaActivitySyncService;
    private final StravaWebhookService stravaWebhookService;
    private final UserRepository userRepository;

    public StravaController(StravaOAuthService stravaOAuthService,
                            StravaActivitySyncService stravaActivitySyncService,
                            StravaWebhookService stravaWebhookService,
                            UserRepository userRepository) {
        this.stravaOAuthService = stravaOAuthService;
        this.stravaActivitySyncService = stravaActivitySyncService;
        this.stravaWebhookService = stravaWebhookService;
        this.userRepository = userRepository;
    }

    @GetMapping("/auth")
    public ResponseEntity<Map<String, String>> authorizeStrava() {
        String authUrl = stravaOAuthService.getAuthorizationUrl();
        Map<String, String> response = new HashMap<>();
        response.put("authorization_url", authUrl);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback")
    public ResponseEntity<?> handleCallback(
            @RequestParam String code,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");

            User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new RuntimeException("User not found"));

            User updatedUser = stravaOAuthService.handleOAuthCallback(code, user.getId());

            // Kick off async backfill so the user's feed populates without blocking the response.
            stravaActivitySyncService.backfillRecentActivitiesAsync(updatedUser.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Strava connected successfully");
            response.put("strava_athlete_id", updatedUser.getStravaAthleteId());
            response.put("display_name", updatedUser.getDisplayName());
            response.put("backfill_status", "started");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Strava callback failed", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Manual re-sync trigger; useful when a user reconnects or for debugging.
     */
    @PostMapping("/sync")
    public ResponseEntity<?> triggerSync(Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new RuntimeException("User not found"));
            stravaActivitySyncService.backfillRecentActivitiesAsync(user.getId());
            return ResponseEntity.ok(Map.of("status", "started"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/disconnect")
    public ResponseEntity<Map<String, String>> disconnectStrava(Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");

            User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new RuntimeException("User not found"));

            stravaOAuthService.disconnectStrava(user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Strava disconnected successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Strava disconnect failed", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Webhook GET endpoint: Strava issues this once when validating the subscription.
     * We must echo `hub.challenge` only when `hub.verify_token` matches our configured token.
     */
    @GetMapping("/webhook")
    public ResponseEntity<?> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.challenge") String challenge,
            @RequestParam(value = "hub.verify_token", required = false) String token) {
        String expected = stravaWebhookService.getVerifyToken();
        if (expected == null || expected.isBlank() || !expected.equals(token)) {
            logger.warning("Strava webhook verification failed (verify_token mismatch)");
            return ResponseEntity.status(403).body(Map.of("error", "verify_token mismatch"));
        }
        if (!"subscribe".equals(mode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported hub.mode"));
        }
        return ResponseEntity.ok(Map.of("hub.challenge", challenge));
    }

    /**
     * Webhook POST endpoint: invoked by Strava on activity create/update/delete.
     * Strava expects a 200 within 2s; we dispatch the heavy work asynchronously.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhookEvent(@RequestBody StravaWebhookEvent event) {
        logger.info(String.format("Strava webhook: %s %s id=%d owner=%d",
            event.getAspectType(), event.getObjectType(), event.getObjectId(), event.getOwnerId()));

        if (event.getObjectType() == null || event.getObjectId() == null || event.getOwnerId() == null) {
            return ResponseEntity.ok().build(); // ack and ignore malformed events
        }

        if ("activity".equals(event.getObjectType())) {
            String aspect = event.getAspectType() == null ? "" : event.getAspectType();
            switch (aspect) {
                case "create":
                case "update":
                    stravaActivitySyncService.handleActivityCreatedOrUpdatedAsync(event.getOwnerId(), event.getObjectId());
                    break;
                case "delete":
                    stravaActivitySyncService.handleActivityDeletedAsync(event.getObjectId());
                    break;
                default:
                    logger.warning("Unknown aspect_type: " + aspect);
            }
        }
        return ResponseEntity.ok().build();
    }
}
