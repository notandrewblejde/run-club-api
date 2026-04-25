package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.UUID;

@Schema(name = "Goal")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Goal {
    @JsonProperty("object")
    public final String object = "goal";

    @JsonProperty("id")
    public UUID id;

    @JsonProperty("club_id")
    public UUID clubId;

    @JsonProperty("name")
    public String name;

    @JsonProperty("target_distance_miles")
    public BigDecimal targetDistanceMiles;

    /** ISO date (yyyy-mm-dd). Goals are date-bounded, not timestamp-bounded. */
    @JsonProperty("start_date")
    public String startDate;

    @JsonProperty("end_date")
    public String endDate;

    @JsonProperty("created")
    public Long created;

    public static Goal from(com.runclub.api.entity.ClubGoal g) {
        if (g == null) return null;
        Goal d = new Goal();
        d.id = g.getId();
        d.clubId = g.getClub() != null ? g.getClub().getId() : null;
        d.name = g.getName();
        d.targetDistanceMiles = g.getTargetDistanceMiles();
        d.startDate = g.getStartDate() != null ? g.getStartDate().toString() : null;
        d.endDate = g.getEndDate() != null ? g.getEndDate().toString() : null;
        d.created = g.getCreatedAt() != null ? g.getCreatedAt().toEpochSecond(ZoneOffset.UTC) : null;
        return d;
    }
}
