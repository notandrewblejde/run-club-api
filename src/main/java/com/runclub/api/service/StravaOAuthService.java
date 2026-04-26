package com.runclub.api.service;

import com.runclub.api.dto.StravaTokenResponse;
import com.runclub.api.entity.User;
import com.runclub.api.repository.UserRepository;
import com.runclub.api.security.TokenCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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

    @Value("${strava.redirect-uri:runclub://localhost/strava-callback}")
    private String redirectUri;

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final TokenCipher tokenCipher;

    public StravaOAuthService(UserRepository userRepository, RestTemplate restTemplate, TokenCipher tokenCipher) {
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
        this.tokenCipher = tokenCipher;
    }

    public String getAuthorizationUrl() {
        return UriComponentsBuilder.fromHttpUrl("https://www.strava.com/oauth/authorize")
            .queryParam("client_id", clientId)
            .queryParam("response_type", "code")
            .queryParam("redirect_uri", redirectUri)
            .queryParam("approval_prompt", "auto")
            .queryParam("scope", "activity:read_all")
            .encode()
            .build()
            .toUriString();
    }

    public User handleOAuthCallback(String code, UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
        tokenRequest.add("client_id", clientId);
        tokenRequest.add("client_secret", clientSecret);
        tokenRequest.add("code", code);
        tokenRequest.add("grant_type", "authorization_code");
        tokenRequest.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(tokenRequest, headers);

        try {
            StravaTokenResponse response = restTemplate.postForObject(STRAVA_TOKEN_URL, entity, StravaTokenResponse.class);
            if (response == null) {
                throw new RuntimeException("Failed to get token from Strava");
            }

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            user.setStravaAthleteId(response.getAthlete().getId());
            user.setStravaAccessToken(tokenCipher.encrypt(response.getAccessToken()));
            user.setStravaRefreshToken(tokenCipher.encrypt(response.getRefreshToken()));
            user.setStravaTokenExpiresAt(toLocalDateTime(response.getExpiresAt()));
            user.setUpdatedAt(LocalDateTime.now());

            return userRepository.save(user);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to exchange Strava code for token", e);
            throw new RuntimeException("Strava OAuth failed: " + e.getMessage(), e);
        }
    }

    public void refreshStravaToken(User user) {
        String refresh = tokenCipher.decrypt(user.getStravaRefreshToken());
        if (refresh == null) {
            throw new RuntimeException("User does not have a refresh token");
        }

        Map<String, Object> refreshRequest = new HashMap<>();
        refreshRequest.put("client_id", clientId);
        refreshRequest.put("client_secret", clientSecret);
        refreshRequest.put("grant_type", "refresh_token");
        refreshRequest.put("refresh_token", refresh);

        try {
            StravaTokenResponse response = restTemplate.postForObject(STRAVA_TOKEN_URL, refreshRequest, StravaTokenResponse.class);
            if (response == null) {
                throw new RuntimeException("Failed to refresh Strava token");
            }

            user.setStravaAccessToken(tokenCipher.encrypt(response.getAccessToken()));
            user.setStravaRefreshToken(tokenCipher.encrypt(response.getRefreshToken()));
            user.setStravaTokenExpiresAt(toLocalDateTime(response.getExpiresAt()));
            user.setUpdatedAt(LocalDateTime.now());

            userRepository.save(user);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to refresh Strava token", e);
            throw new RuntimeException("Token refresh failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a usable access token, refreshing if expired or near-expiry (10 min buffer).
     * Always returns a decrypted token suitable for use in an API request.
     */
    public String getValidAccessToken(User user) {
        if (user.getStravaTokenExpiresAt() != null
                && user.getStravaTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(10))) {
            refreshStravaToken(user);
        }
        return tokenCipher.decrypt(user.getStravaAccessToken());
    }

    public void disconnectStrava(User user) {
        String token = tokenCipher.decrypt(user.getStravaAccessToken());
        if (token == null) {
            return;
        }

        Map<String, Object> deauthRequest = new HashMap<>();
        deauthRequest.put("access_token", token);

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

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }
}
