package com.runclub.api.service;

import com.runclub.api.dto.StravaTokenResponse;
import com.runclub.api.entity.User;
import com.runclub.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class StravaOAuthService {
    private static final Logger logger = Logger.getLogger(StravaOAuthService.class.getName());
    private static final String STRAVA_TOKEN_URL = "https://www.strava.com/oauth/token";
    private static final String STRAVA_DEAUTH_URL = "https://www.strava.com/oauth/deauthorize";

    @Value("${strava.client-id}")
    private String clientId;

    @Value("${strava.client-secret}")
    private String clientSecret;

    @Value("${strava.redirect-uri:http://localhost:3000/strava/callback}")
    private String redirectUri;

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    public StravaOAuthService(UserRepository userRepository, RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
    }

    public String getAuthorizationUrl() {
        return String.format(
            "https://www.strava.com/oauth/authorize?client_id=%s&response_type=code&redirect_uri=%s&approval_prompt=auto&scope=activity:read_all",
            clientId,
            redirectUri
        );
    }

    public User handleOAuthCallback(String code, UUID userId) {
        Map<String, Object> tokenRequest = new HashMap<>();
        tokenRequest.put("client_id", clientId);
        tokenRequest.put("client_secret", clientSecret);
        tokenRequest.put("code", code);
        tokenRequest.put("grant_type", "authorization_code");

        try {
            StravaTokenResponse response = restTemplate.postForObject(STRAVA_TOKEN_URL, tokenRequest, StravaTokenResponse.class);
            if (response == null) {
                throw new RuntimeException("Failed to get token from Strava");
            }

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            user.setStravaAthleteId(response.getAthlete().getId());
            user.setStravaAccessToken(response.getAccessToken());
            user.setStravaRefreshToken(response.getRefreshToken());
            LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(response.getExpiresAt()),
                ZoneId.systemDefault()
            );
            user.setStravaTokenExpiresAt(expiresAt);
            user.setUpdatedAt(LocalDateTime.now());

            return userRepository.save(user);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to exchange Strava code for token", e);
            throw new RuntimeException("Strava OAuth failed: " + e.getMessage(), e);
        }
    }

    public void refreshStravaToken(User user) {
        if (user.getStravaRefreshToken() == null) {
            throw new RuntimeException("User does not have a refresh token");
        }

        Map<String, Object> refreshRequest = new HashMap<>();
        refreshRequest.put("client_id", clientId);
        refreshRequest.put("client_secret", clientSecret);
        refreshRequest.put("grant_type", "refresh_token");
        refreshRequest.put("refresh_token", user.getStravaRefreshToken());

        try {
            StravaTokenResponse response = restTemplate.postForObject(STRAVA_TOKEN_URL, refreshRequest, StravaTokenResponse.class);
            if (response == null) {
                throw new RuntimeException("Failed to refresh Strava token");
            }

            user.setStravaAccessToken(response.getAccessToken());
            user.setStravaRefreshToken(response.getRefreshToken());
            LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(response.getExpiresAt()),
                ZoneId.systemDefault()
            );
            user.setStravaTokenExpiresAt(expiresAt);
            user.setUpdatedAt(LocalDateTime.now());

            userRepository.save(user);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to refresh Strava token", e);
            throw new RuntimeException("Token refresh failed: " + e.getMessage(), e);
        }
    }

    public String getValidAccessToken(User user) {
        if (user.getStravaTokenExpiresAt() != null && user.getStravaTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(10))) {
            refreshStravaToken(user);
        }
        return user.getStravaAccessToken();
    }

    public void disconnectStrava(User user) {
        if (user.getStravaAccessToken() == null) {
            return;
        }

        Map<String, Object> deauthRequest = new HashMap<>();
        deauthRequest.put("access_token", user.getStravaAccessToken());

        try {
            restTemplate.postForObject(STRAVA_DEAUTH_URL, deauthRequest, Object.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to revoke Strava token with Strava API", e);
        }

        user.setStravaAccessToken(null);
        user.setStravaRefreshToken(null);
        user.setStravaTokenExpiresAt(null);
        user.setStravaAthleteId(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}
