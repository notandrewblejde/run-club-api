package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZoneOffset;
import java.util.UUID;

@Schema(name = "ClubMembership")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClubMembership {
    @JsonProperty("object")
    public final String object = "club_membership";

    @JsonProperty("club_id")
    public UUID clubId;

    @JsonProperty("user")
    public User user;

    @JsonProperty("role")
    public String role; // "owner" | "admin" | "member"

    /** Unix seconds; informational only — goal metrics use the goal date window, not join time. */
    @JsonProperty("joined")
    public Long joined;

    public static ClubMembership from(com.runclub.api.entity.ClubMembership m) {
        if (m == null) return null;
        ClubMembership d = new ClubMembership();
        d.clubId = m.getClub() != null ? m.getClub().getId() : null;
        d.user = User.from(m.getUser());
        d.role = m.getRole();
        d.joined = m.getJoinedAt() != null ? m.getJoinedAt().toEpochSecond(ZoneOffset.UTC) : null;
        return d;
    }
}
