package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "GoalProgress")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalProgress {
    @JsonProperty("object")
    public final String object = "goal_progress";

    @JsonProperty("goal_id")
    public UUID goalId;

    @JsonProperty("name")
    public String name;

    @JsonProperty("target_distance_miles")
    public BigDecimal targetDistanceMiles;

    @JsonProperty("total_distance_miles")
    public BigDecimal totalDistanceMiles;

    @JsonProperty("progress_percent")
    public BigDecimal progressPercent;

    @JsonProperty("start_date")
    public String startDate;

    @JsonProperty("end_date")
    public String endDate;
}
