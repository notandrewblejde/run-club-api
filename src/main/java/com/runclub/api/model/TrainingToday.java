package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runclub.api.entity.UserDailyTrainingPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Schema(name = "TrainingToday")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@Builder
public class TrainingToday {

    @JsonProperty("headline")
    private final String headline;

    @JsonProperty("bullets")
    private final List<String> bullets;

    @JsonProperty("progress_hint")
    private final String progressHint;

    @JsonProperty("primary_session")
    private final String primarySession;

    @JsonProperty("rationale")
    private final String rationale;

    @JsonProperty("plan_date")
    private final String planDate;

    @JsonProperty("generated_at")
    private final String generatedAt;

    public static TrainingToday fallbackNoPlanCached() {
        return TrainingToday.builder()
            .headline("Today's movement")
            .bullets(List.of("Set a training goal in AI Coach for a tailored plan."))
            .progressHint("")
            .primarySession("Easy day or rest as you prefer.")
            .rationale("No plan cached yet.")
            .build();
    }

    public static TrainingToday fromPlan(UserDailyTrainingPlan plan, ObjectMapper objectMapper) {
        JsonNode body;
        try {
            body = objectMapper.readTree(plan.getBodyJson());
        } catch (Exception e) {
            body = objectMapper.createObjectNode();
        }
        List<String> bullets = new ArrayList<>();
        if (body.path("bullets").isArray()) {
            for (JsonNode b : body.path("bullets")) {
                bullets.add(b.asText());
            }
        }
        return TrainingToday.builder()
            .headline(plan.getHeadline())
            .bullets(bullets)
            .progressHint(body.path("progress_hint").asText(""))
            .primarySession(body.path("primary_session").asText(""))
            .rationale(body.path("rationale").asText(""))
            .planDate(plan.getPlanDate().toString())
            .generatedAt(plan.getGeneratedAt().toString())
            .build();
    }
}
