package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "UserProfile")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfile {
    @JsonProperty("object")
    public final String object = "user_profile";

    @JsonProperty("id")
    public UUID id;

    @JsonProperty("name")
    public String name;

    @JsonProperty("avatar_url")
    public String avatarUrl;

    @JsonProperty("bio")
    public String bio;

    @JsonProperty("city")
    public String city;

    @JsonProperty("state")
    public String state;

    @JsonProperty("strava_connected")
    public boolean stravaConnected;

    @JsonProperty("followers_count")
    public long followersCount;

    @JsonProperty("following_count")
    public long followingCount;

    @JsonProperty("is_self")
    public boolean isSelf;

    /** Whether the requesting user follows this profile. Null when {@code is_self} is true. */
    @JsonProperty("followed_by_viewer")
    public Boolean followedByViewer;

    @JsonProperty("stats")
    public UserStats stats;
}
