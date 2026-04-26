package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserStats")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserStats {
    @JsonProperty("object")
    public final String object = "user_stats";

    @JsonProperty("total_activities")
    public long totalActivities;

    @JsonProperty("total_distance_miles")
    public double totalDistanceMiles;

    @JsonProperty("total_moving_seconds")
    public long totalMovingSeconds;

    @JsonProperty("total_elevation_ft")
    public double totalElevationFt;

    @JsonProperty("distance_miles_30d")
    public double distanceMiles30d;

    @JsonProperty("activities_30d")
    public long activities30d;

    @JsonProperty("moving_seconds_30d")
    public long movingSeconds30d;
}
