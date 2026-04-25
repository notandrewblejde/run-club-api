package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZoneOffset;
import java.util.UUID;

/** Public-facing summary of a runner. Fully-qualified entity refs avoid name collisions. */
@Schema(name = "User")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {

    @JsonProperty("object")
    public final String object = "user";

    @JsonProperty("id")
    public UUID id;

    @JsonProperty("name")
    public String name;

    @JsonProperty("avatar_url")
    public String avatarUrl;

    @JsonProperty("created")
    public Long created;

    public static User from(com.runclub.api.entity.User u) {
        if (u == null) return null;
        User d = new User();
        d.id = u.getId();
        d.name = u.getDisplayName();
        d.avatarUrl = u.getProfilePicUrl();
        if (u.getCreatedAt() != null) {
            d.created = u.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
        }
        return d;
    }
}
