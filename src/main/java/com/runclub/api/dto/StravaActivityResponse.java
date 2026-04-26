package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaActivityResponse {
    private Long id;
    private String name;

    @JsonProperty("sport_type")
    private String sportType;

    private String type;

    private Double distance; // meters

    @JsonProperty("moving_time")
    private Integer movingTime;

    @JsonProperty("elapsed_time")
    private Integer elapsedTime;

    @JsonProperty("total_elevation_gain")
    private Double totalElevationGain; // meters

    @JsonProperty("elev_high")
    private Double elevHigh;

    @JsonProperty("average_heartrate")
    private Double averageHeartrate;

    @JsonProperty("max_heartrate")
    private Double maxHeartrate;

    @JsonProperty("start_date")
    private OffsetDateTime startDate;

    @JsonProperty("location_city")
    private String locationCity;

    @JsonProperty("location_state")
    private String locationState;

    private Map map;

    @JsonProperty("kudos_count")
    private Integer kudosCount;

    @JsonProperty("comment_count")
    private Integer commentCount;

    @JsonProperty("pr_count")
    private Integer prCount;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<Photo> photos;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSportType() { return sportType; }
    public void setSportType(String sportType) { this.sportType = sportType; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }
    public Integer getMovingTime() { return movingTime; }
    public void setMovingTime(Integer movingTime) { this.movingTime = movingTime; }
    public Integer getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(Integer elapsedTime) { this.elapsedTime = elapsedTime; }
    public Double getTotalElevationGain() { return totalElevationGain; }
    public void setTotalElevationGain(Double totalElevationGain) { this.totalElevationGain = totalElevationGain; }
    public Double getElevHigh() { return elevHigh; }
    public void setElevHigh(Double elevHigh) { this.elevHigh = elevHigh; }
    public Double getAverageHeartrate() { return averageHeartrate; }
    public void setAverageHeartrate(Double averageHeartrate) { this.averageHeartrate = averageHeartrate; }
    public Double getMaxHeartrate() { return maxHeartrate; }
    public void setMaxHeartrate(Double maxHeartrate) { this.maxHeartrate = maxHeartrate; }
    public OffsetDateTime getStartDate() { return startDate; }
    public void setStartDate(OffsetDateTime startDate) { this.startDate = startDate; }
    public String getLocationCity() { return locationCity; }
    public void setLocationCity(String locationCity) { this.locationCity = locationCity; }
    public String getLocationState() { return locationState; }
    public void setLocationState(String locationState) { this.locationState = locationState; }
    public Map getMap() { return map; }
    public void setMap(Map map) { this.map = map; }
    public Integer getKudosCount() { return kudosCount; }
    public void setKudosCount(Integer kudosCount) { this.kudosCount = kudosCount; }
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public Integer getPrCount() { return prCount; }
    public void setPrCount(Integer prCount) { this.prCount = prCount; }
    public List<Photo> getPhotos() { return photos; }
    public void setPhotos(List<Photo> photos) { this.photos = photos; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Map {
        private String id;

        @JsonProperty("summary_polyline")
        private String summaryPolyline;

        private String polyline;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSummaryPolyline() { return summaryPolyline; }
        public void setSummaryPolyline(String summaryPolyline) { this.summaryPolyline = summaryPolyline; }
        public String getPolyline() { return polyline; }
        public void setPolyline(String polyline) { this.polyline = polyline; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Photo {
        private Urls urls;

        public Urls getUrls() { return urls; }
        public void setUrls(Urls urls) { this.urls = urls; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Urls {
        @JsonProperty("100")
        private String thumb;

        @JsonProperty("600")
        private String full;

        public String getThumb() { return thumb; }
        public void setThumb(String thumb) { this.thumb = thumb; }
        public String getFull() { return full; }
        public void setFull(String full) { this.full = full; }
    }
}
