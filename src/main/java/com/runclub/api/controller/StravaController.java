package com.runclub.api.controller;

import com.runclub.api.entity.User;
import com.runclub.api.repository.UserRepository;
import com.runclub.api.service.StravaOAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/v1/strava")
public class StravaController {
    private static final Logger logger = Logger.getLogger(StravaController.class.getName());

    private final StravaOAuthService stravaOAuthService;
    private final UserRepository userRepository;

    public StravaController(StravaOAuthService stravaOAuthService, UserRepository userRepository) {
        this.stravaOAuthService = stravaOAuthService;
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

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Strava connected successfully");
            response.put("strava_athlete_id", updatedUser.getStravaAthleteId());
            response.put("display_name", updatedUser.getDisplayName());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Strava callback failed", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
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

    @GetMapping("/webhook")
    public ResponseEntity<Map<String, String>> verifyWebhook(@RequestParam("hub.challenge") String challenge) {
        Map<String, String> response = new HashMap<>();
        response.put("hub.challenge", challenge);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhookEvent(@RequestBody Map<String, Object> event) {
        logger.info("Received Strava webhook event: " + event);
        // TODO: Process activity create/update events
        // For now, just acknowledge receipt
        return ResponseEntity.ok().build();
    }
}
