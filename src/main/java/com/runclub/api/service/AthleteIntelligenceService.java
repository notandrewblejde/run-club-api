package com.runclub.api.service;

import com.runclub.api.entity.Activity;
import com.runclub.api.repository.ActivityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AthleteIntelligenceService {

    private final ActivityRepository activityRepository;
    private final RestTemplate restTemplate;

    @Value("${anthropic.api-key:${ANTHROPIC_API_KEY:}}")
    private String anthropicApiKey;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-opus-4-7";
    private static final int MAX_TOKENS = 1024;

    public AthleteIntelligenceService(ActivityRepository activityRepository, RestTemplate restTemplate) {
        this.activityRepository = activityRepository;
        this.restTemplate = restTemplate;
    }

    public String generateActivitySummary(UUID activityId) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new RuntimeException("Activity not found"));

        String prompt = buildActivityPrompt(activity);
        return callClaudeAPI(prompt);
    }

    private String buildActivityPrompt(Activity activity) {
        return String.format(
            "You are a running coach providing brief, encouraging feedback on a runner's workout. " +
            "Based on this activity data, provide a 2-3 sentence summary highlighting the key achievement and one coaching tip.\n\n" +
            "Activity: %s\n" +
            "Distance: %.2f miles\n" +
            "Time: %d minutes\n" +
            "Pace: %s\n" +
            "Elevation Gain: %.0f ft\n" +
            "Location: %s, %s\n" +
            "Heart Rate: %d-%d bpm\n" +
            "Personal Record: %s",
            activity.getName(),
            activity.getDistanceMiles() != null ? activity.getDistanceMiles().doubleValue() : 0,
            activity.getMovingTimeSeconds() != null ? activity.getMovingTimeSeconds() / 60 : 0,
            activity.getAvgPaceDisplay() != null ? activity.getAvgPaceDisplay() : "N/A",
            activity.getElevationGainFt() != null ? activity.getElevationGainFt().doubleValue() : 0,
            activity.getCity() != null ? activity.getCity() : "Unknown",
            activity.getState() != null ? activity.getState() : "Unknown",
            activity.getAvgHeartRateBpm() != null ? activity.getAvgHeartRateBpm() : 0,
            activity.getMaxHeartRateBpm() != null ? activity.getMaxHeartRateBpm() : 0,
            activity.getIsPersonalRecord() != null && activity.getIsPersonalRecord() ? "Yes" : "No"
        );
    }

    private String callClaudeAPI(String prompt) {
        if (anthropicApiKey == null || anthropicApiKey.isEmpty()) {
            return "AI summary unavailable (API key not configured)";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", anthropicApiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", MODEL);
            requestBody.put("max_tokens", MAX_TOKENS);
            requestBody.put("messages", new Object[]{
                Map.of("role", "user", "content", prompt)
            });

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(CLAUDE_API_URL, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                java.util.List<?> content = (java.util.List<?>) response.getBody().get("content");
                if (content != null && !content.isEmpty()) {
                    Map<String, Object> firstContent = (Map<String, Object>) content.get(0);
                    return (String) firstContent.get("text");
                }
            }

            return "Unable to generate summary at this time";
        } catch (Exception e) {
            return "Error generating AI summary: " + e.getMessage();
        }
    }
}
