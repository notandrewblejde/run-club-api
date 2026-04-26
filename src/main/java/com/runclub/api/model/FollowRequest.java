package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZoneOffset;
import java.util.UUID;

@Schema(name = "FollowRequest")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FollowRequest {
    @JsonProperty("object")
    public final String object = "follow_request";

    @JsonProperty("id")
    public UUID id;

    /** The user who sent the request (i.e. the would-be follower). */
    @JsonProperty("requester")
    public User requester;

    @JsonProperty("status")
    public String status; // "pending"

    @JsonProperty("created")
    public Long created;

    public static FollowRequest from(com.runclub.api.entity.Follow f) {
        if (f == null) return null;
        FollowRequest r = new FollowRequest();
        r.id = f.getId();
        r.requester = User.from(f.getFollower());
        r.status = f.getStatus();
        r.created = f.getCreatedAt() != null ? f.getCreatedAt().toEpochSecond(ZoneOffset.UTC) : null;
        return r;
    }
}
