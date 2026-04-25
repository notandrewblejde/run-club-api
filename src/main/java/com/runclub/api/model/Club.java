package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZoneOffset;
import java.util.UUID;

@Schema(name = "Club")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Club {
    @JsonProperty("object")
    public final String object = "club";

    @JsonProperty("id")
    public UUID id;

    @JsonProperty("name")
    public String name;

    @JsonProperty("description")
    public String description;

    @JsonProperty("privacy_level")
    public String privacyLevel; // "public" | "private"

    @JsonProperty("created_by_user_id")
    public UUID createdByUserId;

    @JsonProperty("member_count")
    public Integer memberCount;

    /** The viewer's role in this club, if a member. */
    @JsonProperty("viewer_role")
    public String viewerRole;

    @JsonProperty("created")
    public Long created;

    public static Club from(com.runclub.api.entity.Club c) {
        if (c == null) return null;
        Club d = new Club();
        d.id = c.getId();
        d.name = c.getName();
        d.description = c.getDescription();
        d.privacyLevel = c.getPrivacyLevel();
        d.createdByUserId = c.getCreatedByUser() != null ? c.getCreatedByUser().getId() : null;
        d.created = c.getCreatedAt() != null ? c.getCreatedAt().toEpochSecond(ZoneOffset.UTC) : null;
        return d;
    }
}
