package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runclub.api.entity.UserDailyTrainingPlan;
import com.runclub.api.entity.UserTrainingProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Optional;

@Schema(name = "TrainingGoal")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@Builder
public class TrainingGoal {

    @JsonProperty("goal_text")
    private final String goalText;

    @JsonProperty("interpretation_json")
    private final String interpretationJson;

    @JsonProperty("interpretation")
    private final JsonNode interpretation;

    @JsonProperty("interpretation_updated_at")
    private final String interpretationUpdatedAt;

    @JsonProperty("daily_plan")
    private final DailyTrainingPlan dailyPlan;

    @Schema(name = "DailyTrainingPlan")
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    @Getter
    @Builder
    public static class DailyTrainingPlan {
        @JsonProperty("plan_date")
        private final String planDate;
        @JsonProperty("headline")
        private final String headline;
        @JsonProperty("generated_at")
        private final String generatedAt;
        @JsonProperty("body")
        private final JsonNode body;
    }

    public static TrainingGoal fromProfile(
        UserTrainingProfile profile,
        Optional<UserDailyTrainingPlan> planOpt,
        ObjectMapper objectMapper) {
        String goalText = profile.getGoalText() != null ? profile.getGoalText() : "";
        String ij = profile.getInterpretationJson();
        JsonNode interpretationNode = null;
        if (ij != null && !ij.isBlank()) {
            try {
                interpretationNode = objectMapper.readTree(ij);
            } catch (Exception e) {
                interpretationNode = null;
            }
        }
        DailyTrainingPlan daily = planOpt.map(p -> dailyPlanFrom(p, objectMapper)).orElse(null);
        return TrainingGoal.builder()
            .goalText(goalText)
            .interpretationJson(ij)
            .interpretation(interpretationNode)
            .interpretationUpdatedAt(
                profile.getInterpretationUpdatedAt() != null
                    ? profile.getInterpretationUpdatedAt().toString()
                    : null)
            .dailyPlan(daily)
            .build();
    }

    private static DailyTrainingPlan dailyPlanFrom(UserDailyTrainingPlan plan, ObjectMapper objectMapper) {
        JsonNode bodyNode;
        try {
            bodyNode = objectMapper.readTree(plan.getBodyJson());
        } catch (Exception e) {
            bodyNode = objectMapper.createObjectNode();
        }
        return DailyTrainingPlan.builder()
            .planDate(plan.getPlanDate().toString())
            .headline(plan.getHeadline())
            .generatedAt(plan.getGeneratedAt().toString())
            .body(bodyNode)
            .build();
    }
}
