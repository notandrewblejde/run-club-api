package com.runclub.api.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "strava_id", unique = true, nullable = false)
    private Long stravaActivityId;

    @Column(nullable = false)
    private String name;

    @Column(name = "sport_type")
    private String type;

    @Column(name = "distance_meters")
    private BigDecimal distanceMeters;

    @Column(name = "moving_time_secs")
    private Integer movingTimeSeconds;

    @Column(name = "elapsed_time_secs")
    private Integer elapsedTimeSeconds;

    @Column(name = "elevation_gain_ft")
    private BigDecimal elevationGainFt;

    @Column(name = "max_elevation_ft")
    private BigDecimal maxElevationFt;

    @Column(name = "avg_heart_rate_bpm")
    private Integer avgHeartRateBpm;

    @Column(name = "max_heart_rate_bpm")
    private Integer maxHeartRateBpm;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "map_polyline", columnDefinition = "TEXT")
    private String mapPolyline;

    @Column(name = "photos", columnDefinition = "TEXT[]")
    private String[] photos;

    /** User-written caption; never set by Strava sync. */
    @Column(name = "user_note", columnDefinition = "TEXT")
    private String userNote;

    /** App-uploaded photo URLs (S3); never set by Strava sync. */
    @Column(name = "app_photos", columnDefinition = "TEXT[]")
    private String[] appPhotos;

    @Column(name = "is_personal_record")
    private Boolean isPersonalRecord = false;

    @Column(name = "kudos_count")
    private Integer kudosCount = 0;

    @Column(name = "comment_count")
    private Integer commentCount = 0;

    /** Short coach insight from activity telemetry; generated async after Strava import. */
    @Column(name = "ai_coach_summary", columnDefinition = "TEXT")
    private String aiCoachSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Long getStravaActivityId() { return stravaActivityId; }
    public void setStravaActivityId(Long stravaActivityId) { this.stravaActivityId = stravaActivityId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(BigDecimal distanceMeters) { this.distanceMeters = distanceMeters; }

    public Integer getMovingTimeSeconds() { return movingTimeSeconds; }
    public void setMovingTimeSeconds(Integer movingTimeSeconds) { this.movingTimeSeconds = movingTimeSeconds; }

    public Integer getElapsedTimeSeconds() { return elapsedTimeSeconds; }
    public void setElapsedTimeSeconds(Integer elapsedTimeSeconds) { this.elapsedTimeSeconds = elapsedTimeSeconds; }

    public BigDecimal getElevationGainFt() { return elevationGainFt; }
    public void setElevationGainFt(BigDecimal elevationGainFt) { this.elevationGainFt = elevationGainFt; }

    public BigDecimal getMaxElevationFt() { return maxElevationFt; }
    public void setMaxElevationFt(BigDecimal maxElevationFt) { this.maxElevationFt = maxElevationFt; }

    public Integer getAvgHeartRateBpm() { return avgHeartRateBpm; }
    public void setAvgHeartRateBpm(Integer avgHeartRateBpm) { this.avgHeartRateBpm = avgHeartRateBpm; }

    public Integer getMaxHeartRateBpm() { return maxHeartRateBpm; }
    public void setMaxHeartRateBpm(Integer maxHeartRateBpm) { this.maxHeartRateBpm = maxHeartRateBpm; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getMapPolyline() { return mapPolyline; }
    public void setMapPolyline(String mapPolyline) { this.mapPolyline = mapPolyline; }

    public String[] getPhotos() { return photos; }
    public void setPhotos(String[] photos) { this.photos = photos; }

    public String getUserNote() { return userNote; }
    public void setUserNote(String userNote) { this.userNote = userNote; }

    public String[] getAppPhotos() { return appPhotos; }
    public void setAppPhotos(String[] appPhotos) { this.appPhotos = appPhotos; }

    public Boolean getIsPersonalRecord() { return isPersonalRecord; }
    public void setIsPersonalRecord(Boolean isPersonalRecord) { this.isPersonalRecord = isPersonalRecord; }

    public Integer getKudosCount() { return kudosCount; }
    public void setKudosCount(Integer kudosCount) { this.kudosCount = kudosCount; }

    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }

    public String getAiCoachSummary() { return aiCoachSummary; }
    public void setAiCoachSummary(String aiCoachSummary) { this.aiCoachSummary = aiCoachSummary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public BigDecimal getDistanceMiles() {
        if (distanceMeters == null) return null;
        return distanceMeters.multiply(new BigDecimal("0.000621371"));
    }

    public String getAvgPaceDisplay() {
        if (movingTimeSeconds == null || distanceMeters == null) return null;
        BigDecimal miles = getDistanceMiles();
        if (miles == null || miles.compareTo(BigDecimal.ZERO) == 0) return null;
        BigDecimal secsPerMile = new BigDecimal(movingTimeSeconds).divide(miles, 0, java.math.RoundingMode.HALF_UP);
        long mins = secsPerMile.longValue() / 60;
        long secs = secsPerMile.longValue() % 60;
        return String.format("%d:%02d /mi", mins, secs);
    }
}
