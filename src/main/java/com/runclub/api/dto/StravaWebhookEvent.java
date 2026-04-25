package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaWebhookEvent {
    @JsonProperty("object_type")
    private String objectType; // "activity" or "athlete"

    @JsonProperty("object_id")
    private Long objectId; // activity id or athlete id

    @JsonProperty("aspect_type")
    private String aspectType; // "create", "update", "delete"

    @JsonProperty("owner_id")
    private Long ownerId; // strava athlete id

    @JsonProperty("subscription_id")
    private Long subscriptionId;

    @JsonProperty("event_time")
    private Long eventTime;

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public Long getObjectId() { return objectId; }
    public void setObjectId(Long objectId) { this.objectId = objectId; }
    public String getAspectType() { return aspectType; }
    public void setAspectType(String aspectType) { this.aspectType = aspectType; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public Long getEventTime() { return eventTime; }
    public void setEventTime(Long eventTime) { this.eventTime = eventTime; }
}
