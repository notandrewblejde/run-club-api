package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "GoalContribution")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@Builder
public class ClubGoalContribution {

    @JsonProperty("object")
    private final String object;

    @JsonProperty("id")
    private final UUID id;

    @JsonProperty("goal_id")
    private final UUID goalId;

    @JsonProperty("distance_miles")
    private final BigDecimal distanceMiles;

    public static ClubGoalContribution from(com.runclub.api.entity.GoalContribution c) {
        return ClubGoalContribution.builder()
            .object("goal_contribution")
            .id(c.getId())
            .goalId(c.getGoal().getId())
            .distanceMiles(c.getDistanceMiles())
            .build();
    }
}
