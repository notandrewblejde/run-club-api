package com.runclub.api.service;

import com.runclub.api.entity.StravaWebhookSubscription;
import com.runclub.api.repository.StravaWebhookSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages a single application-level Strava webhook subscription.
 * On startup, registers a subscription if one isn't already recorded
 * and a callback URL is configured. Strava only allows one subscription
 * per application, so we coordinate via the strava_webhook_subscription table.
 */
@Service
public class StravaWebhookService {
    private static final Logger logger = Logger.getLogger(StravaWebhookService.class.getName());
    private static final String SUBSCRIPTIONS_URL = "https://www.strava.com/api/v3/push_subscriptions";

    @Value("${strava.client-id}")
    private String clientId;

    @Value("${strava.client-secret}")
    private String clientSecret;

    @Value("${strava.webhook.callback-url:}")
    private String callbackUrl;

    @Value("${strava.webhook.verify-token:}")
    private String verifyToken;

    @Value("${strava.webhook.auto-subscribe:false}")
    private boolean autoSubscribe;

    private final StravaWebhookSubscriptionRepository repository;
    private final RestTemplate restTemplate;

    public StravaWebhookService(StravaWebhookSubscriptionRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    @PostConstruct
    public void ensureSubscription() {
        if (!autoSubscribe) {
            logger.info("Strava webhook auto-subscribe disabled");
            return;
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            logger.warning("strava.webhook.callback-url is not configured; skipping subscription");
            return;
        }
        if (verifyToken == null || verifyToken.isBlank()) {
            logger.warning("strava.webhook.verify-token is not configured; skipping subscription");
            return;
        }
        try {
            Optional<StravaWebhookSubscription> existing = repository.findById(1);
            if (existing.isPresent() && callbackUrl.equals(existing.get().getCallbackUrl())) {
                logger.info("Strava webhook subscription already recorded: " + existing.get().getSubscriptionId());
                return;
            }
            // Either no record, or callback URL changed: drop any prior subscription.
            existing.ifPresent(prior -> deleteSubscriptionRemote(prior.getSubscriptionId()));

            Long id = createSubscriptionRemote();
            StravaWebhookSubscription record = existing.orElseGet(StravaWebhookSubscription::new);
            record.setId(1);
            record.setSubscriptionId(id);
            record.setCallbackUrl(callbackUrl);
            repository.save(record);
            logger.info("Registered Strava webhook subscription " + id);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to register Strava webhook subscription", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Long createSubscriptionRemote() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("callback_url", callbackUrl);
        body.add("verify_token", verifyToken);

        ResponseEntity<Map> resp = restTemplate.postForEntity(SUBSCRIPTIONS_URL,
            new HttpEntity<>(body, headers), Map.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Strava subscription creation failed: " + resp.getStatusCode());
        }
        Object id = resp.getBody().get("id");
        if (id instanceof Number) return ((Number) id).longValue();
        throw new RuntimeException("Strava subscription response missing id: " + resp.getBody());
    }

    private void deleteSubscriptionRemote(Long subscriptionId) {
        try {
            String url = SUBSCRIPTIONS_URL + "/" + subscriptionId
                + "?client_id=" + clientId + "&client_secret=" + clientSecret;
            restTemplate.delete(url);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to delete prior Strava subscription " + subscriptionId, e);
        }
    }
}
