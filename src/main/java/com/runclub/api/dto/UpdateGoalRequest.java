package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** Partial update — omit fields you do not want to change. */
public class UpdateGoalRequest {
    @JsonProperty("name")
    public String name;

    @DecimalMin(value = "0.1", inclusive = true, message = "Target distance must be at least 0.1 miles")
    @JsonProperty("target_distance_miles")
    public BigDecimal targetDistanceMiles;

    @JsonProperty("start_date")
    public String startDate;

    @JsonProperty("end_date")
    public String endDate;
}
