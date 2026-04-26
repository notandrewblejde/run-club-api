package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZoneOffset;
import java.util.UUID;

@Schema(name = "Post")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Post {
    @JsonProperty("object")
    public final String object = "post";

    @JsonProperty("id")
    public UUID id;

    @JsonProperty("club_id")
    public UUID clubId;

    @JsonProperty("author")
    public User author;

    @JsonProperty("content")
    public String content;

    @JsonProperty("photos")
    public String[] photos;

    @JsonProperty("related_activity_id")
    public UUID relatedActivityId;

    @JsonProperty("created")
    public Long created;

    public static Post from(com.runclub.api.entity.Post p) {
        if (p == null) return null;
        Post d = new Post();
        d.id = p.getId();
        d.clubId = p.getClub() != null ? p.getClub().getId() : null;
        d.author = User.from(p.getAuthor());
        d.content = p.getContent();
        d.photos = p.getPhotoUrls();
        d.relatedActivityId = p.getRelatedActivity() != null ? p.getRelatedActivity().getId() : null;
        d.created = p.getCreatedAt() != null ? p.getCreatedAt().toEpochSecond(ZoneOffset.UTC) : null;
        return d;
    }
}
