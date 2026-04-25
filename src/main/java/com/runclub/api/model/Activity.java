package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.UUID;

@Schema(name = "Activity")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Activity {

    @JsonProperty("object")
    public final String object = "activity";

    @JsonProperty("id")
    public UUID id;

    @JsonProperty("strava_id")
    public Long stravaId;

    @JsonProperty("user")
    public User user;

    @JsonProperty("name")
    public String name;

    @JsonProperty("sport_type")
    public String sportType;

    /** Unix epoch (seconds). */
    @JsonProperty("start_date")
    public Long startDate;

    @JsonProperty("city")
    public String city;

    @JsonProperty("state")
    public String state;

    @JsonProperty("distance_meters")
    public BigDecimal distanceMeters;

    @JsonProperty("distance_miles")
    public BigDecimal distanceMiles;

    @JsonProperty("moving_time_secs")
    public Integer movingTimeSecs;

    @JsonProperty("elapsed_time_secs")
    public Integer elapsedTimeSecs;

    @JsonProperty("avg_pace_secs_per_mile")
    public Long avgPaceSecsPerMile;

    @JsonProperty("avg_pace_display")
    public String avgPaceDisplay;

    @JsonProperty("elevation_gain_ft")
    public BigDecimal elevationGainFt;

    @JsonProperty("max_elevation_ft")
    public BigDecimal maxElevationFt;

    @JsonProperty("avg_heart_rate_bpm")
    public Integer avgHeartRateBpm;

    @JsonProperty("max_heart_rate_bpm")
    public Integer maxHeartRateBpm;

    @JsonProperty("map_polyline")
    public String mapPolyline;

    @JsonProperty("photos")
    public String[] photos;

    @JsonProperty("kudos_count")
    public int kudosCount;

    @JsonProperty("comment_count")
    public int commentCount;

    @JsonProperty("personal_record")
    public boolean personalRecord;

    @JsonProperty("kudoed_by_viewer")
    public Boolean kudoedByViewer;

    @JsonProperty("owned_by_viewer")
    public Boolean ownedByViewer;

    @JsonProperty("created")
    public Long created;

    public static Activity from(com.runclub.api.entity.Activity a) {
        if (a == null) return null;
        Activity d = new Activity();
        d.id = a.getId();
        d.stravaId = a.getStravaActivityId();
        d.user = User.from(a.getUser());
        d.name = a.getName();
        d.sportType = a.getType();
        d.startDate = a.getStartDate() != null ? a.getStartDate().toEpochSecond(ZoneOffset.UTC) : null;
        d.city = a.getCity();
        d.state = a.getState();
        d.distanceMeters = a.getDistanceMeters();
        d.distanceMiles = a.getDistanceMiles();
        d.movingTimeSecs = a.getMovingTimeSeconds();
        d.elapsedTimeSecs = a.getElapsedTimeSeconds();
        if (a.getMovingTimeSeconds() != null && d.distanceMiles != null && d.distanceMiles.compareTo(BigDecimal.ZERO) > 0) {
            d.avgPaceSecsPerMile = new BigDecimal(a.getMovingTimeSeconds())
                .divide(d.distanceMiles, 0, java.math.RoundingMode.HALF_UP)
                .longValue();
        }
        d.avgPaceDisplay = a.getAvgPaceDisplay();
        d.elevationGainFt = a.getElevationGainFt();
        d.maxElevationFt = a.getMaxElevationFt();
        d.avgHeartRateBpm = a.getAvgHeartRateBpm();
        d.maxHeartRateBpm = a.getMaxHeartRateBpm();
        d.mapPolyline = a.getMapPolyline();
        d.photos = a.getPhotos();
        d.kudosCount = a.getKudosCount() == null ? 0 : a.getKudosCount();
        d.commentCount = a.getCommentCount() == null ? 0 : a.getCommentCount();
        d.personalRecord = Boolean.TRUE.equals(a.getIsPersonalRecord());
        d.created = a.getCreatedAt() != null ? a.getCreatedAt().toEpochSecond(ZoneOffset.UTC) : null;
        return d;
    }
}
