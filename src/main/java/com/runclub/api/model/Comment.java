package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZoneOffset;
import java.util.UUID;

@Schema(name = "Comment")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Comment {
    @JsonProperty("object")
    public final String object = "comment";

    @JsonProperty("id")
    public UUID id;

    @JsonProperty("activity_id")
    public UUID activityId;

    @JsonProperty("user")
    public User user;

    @JsonProperty("content")
    public String content;

    @JsonProperty("created")
    public Long created;

    public static Comment from(com.runclub.api.entity.ActivityComment c) {
        if (c == null) return null;
        Comment d = new Comment();
        d.id = c.getId();
        d.activityId = c.getActivity() != null ? c.getActivity().getId() : null;
        d.user = User.from(c.getUser());
        d.content = c.getContent();
        d.created = c.getCreatedAt() != null ? c.getCreatedAt().toEpochSecond(ZoneOffset.UTC) : null;
        return d;
    }
}
