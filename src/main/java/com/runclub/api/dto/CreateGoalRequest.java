package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateGoalRequest {
    @NotBlank
    @JsonProperty("name")
    public String name;

    @NotNull
    @DecimalMin("0.1")
    @JsonProperty("target_distance_miles")
    public BigDecimal targetDistanceMiles;

    @NotBlank
    @JsonProperty("start_date")
    public String startDate;

    @NotBlank
    @JsonProperty("end_date")
    public String endDate;
}
