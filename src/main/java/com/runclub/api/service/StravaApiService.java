package com.runclub.api.service;

import com.runclub.api.dto.StravaActivityResponse;
import com.runclub.api.entity.User;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin wrapper around the Strava v3 API. Handles auth header injection and
 * one-shot retry on 401 by triggering a token refresh through {@link StravaOAuthService}.
 */
@Service
public class StravaApiService {
    private static final Logger logger = Logger.getLogger(StravaApiService.class.getName());
    private static final String STRAVA_API_BASE = "https://www.strava.com/api/v3";

    private final RestTemplate restTemplate;
    private final StravaOAuthService stravaOAuthService;

    public StravaApiService(RestTemplate restTemplate, StravaOAuthService stravaOAuthService) {
        this.restTemplate = restTemplate;
        this.stravaOAuthService = stravaOAuthService;
    }

    public StravaActivityResponse fetchActivity(User user, long activityId) {
        String url = STRAVA_API_BASE + "/activities/" + activityId + "?include_all_efforts=false";
        return executeGet(user, url, StravaActivityResponse.class);
    }

    /**
     * List the authenticated athlete's activities. Strava API supports
     * `before` (epoch seconds) and `after` (epoch seconds) for windowing,
     * and per_page/page for pagination.
     */
    public List<StravaActivityResponse> listAthleteActivities(User user, Long after, int page, int perPage) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(STRAVA_API_BASE + "/athlete/activities")
            .queryParam("page", page)
            .queryParam("per_page", Math.min(perPage, 200));
        if (after != null) {
            builder.queryParam("after", after);
        }
        StravaActivityResponse[] result = executeGet(user, builder.toUriString(), StravaActivityResponse[].class);
        return result == null ? Collections.emptyList() : Arrays.asList(result);
    }

    private <T> T executeGet(User user, String url, Class<T> responseType) {
        String token = stravaOAuthService.getValidAccessToken(user);
        try {
            return restTemplate.exchange(url, HttpMethod.GET, authEntity(token), responseType).getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            // Token may have been revoked or rotated under our feet; refresh once and retry.
            logger.log(Level.WARNING, "Strava 401, forcing token refresh and retrying", e);
            stravaOAuthService.refreshStravaToken(user);
            String fresh = stravaOAuthService.getValidAccessToken(user);
            return restTemplate.exchange(url, HttpMethod.GET, authEntity(fresh), responseType).getBody();
        }
    }

    private HttpEntity<Void> authEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }
}
