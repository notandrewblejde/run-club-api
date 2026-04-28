package com.runclub.api.dto.health;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One workout from Apple Health or Health Connect, normalized before upsert into {@code activities}.
 */
public class HealthWorkoutImportItem {

    @NotBlank
    @Pattern(regexp = "apple_health|health_connect", message = "import_source must be apple_health or health_connect")
    @JsonProperty("import_source")
    private String importSource;

    @NotBlank
    @Size(max = 128)
    @JsonProperty("external_id")
    private String externalId;

    @NotBlank
    @Size(max = 255)
    @JsonProperty("name")
    private String name;

    @NotNull
    @JsonProperty("start_date_epoch_seconds")
    private Long startDateEpochSeconds;

    @JsonProperty("distance_meters")
    private BigDecimal distanceMeters;

    @JsonProperty("moving_time_secs")
    private Integer movingTimeSeconds;

    /** Google-encoded polyline (lat,lng pairs), optional. */
    @JsonProperty("map_polyline")
    private String mapPolyline;

    public String getImportSource() {
        return importSource;
    }

    public void setImportSource(String importSource) {
        this.importSource = importSource;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getStartDateEpochSeconds() {
        return startDateEpochSeconds;
    }

    public void setStartDateEpochSeconds(Long startDateEpochSeconds) {
        this.startDateEpochSeconds = startDateEpochSeconds;
    }

    public BigDecimal getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(BigDecimal distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Integer getMovingTimeSeconds() {
        return movingTimeSeconds;
    }

    public void setMovingTimeSeconds(Integer movingTimeSeconds) {
        this.movingTimeSeconds = movingTimeSeconds;
    }

    public String getMapPolyline() {
        return mapPolyline;
    }

    public void setMapPolyline(String mapPolyline) {
        this.mapPolyline = mapPolyline;
    }
}
