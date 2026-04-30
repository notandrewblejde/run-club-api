package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.runclub.api.entity.UserTrainingGoalFeedback;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(name = "GoalFeedbackMessage")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@Builder
public class GoalFeedbackMessage {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("role")
    private final String role;

    @JsonProperty("content")
    private final String content;

    @JsonProperty("created_at")
    private final String createdAt;

    public static GoalFeedbackMessage from(UserTrainingGoalFeedback f) {
        return GoalFeedbackMessage.builder()
            .id(f.getId().toString())
            .role(f.getRole())
            .content(f.getContent())
            .createdAt(f.getCreatedAt().toString())
            .build();
    }
}
