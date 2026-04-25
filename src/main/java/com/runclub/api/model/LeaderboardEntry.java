package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "LeaderboardEntry")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeaderboardEntry {
    @JsonProperty("object")
    public final String object = "leaderboard_entry";

    @JsonProperty("rank")
    public int rank;

    @JsonProperty("user")
    public User user;

    @JsonProperty("total_distance_miles")
    public BigDecimal totalDistanceMiles;
}
