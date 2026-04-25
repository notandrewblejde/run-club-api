package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZoneOffset;
import java.util.UUID;

@Schema(name = "Follow")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Follow {
    @JsonProperty("object")
    public final String object = "follow";

    @JsonProperty("follower_id")
    public UUID followerId;

    @JsonProperty("following_id")
    public UUID followingId;

    @JsonProperty("user")
    public User user;

    @JsonProperty("created")
    public Long created;

    public static Follow follower(com.runclub.api.entity.Follow f) {
        if (f == null) return null;
        Follow d = new Follow();
        d.followerId = f.getFollower() != null ? f.getFollower().getId() : null;
        d.followingId = f.getFollowing() != null ? f.getFollowing().getId() : null;
        d.user = User.from(f.getFollower());
        d.created = f.getCreatedAt() != null ? f.getCreatedAt().toEpochSecond(ZoneOffset.UTC) : null;
        return d;
    }

    public static Follow following(com.runclub.api.entity.Follow f) {
        if (f == null) return null;
        Follow d = new Follow();
        d.followerId = f.getFollower() != null ? f.getFollower().getId() : null;
        d.followingId = f.getFollowing() != null ? f.getFollowing().getId() : null;
        d.user = User.from(f.getFollowing());
        d.created = f.getCreatedAt() != null ? f.getCreatedAt().toEpochSecond(ZoneOffset.UTC) : null;
        return d;
    }
}
