package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.runclub.api.entity.Activity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "ClubFeedActivityItem")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@SuperBuilder
public class ClubFeedActivityItem extends ClubFeedItem {

    @JsonProperty("type")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = { "activity" })
    @Builder.Default
    private final String type = "activity";

    @JsonProperty("id")
    private final UUID id;

    @JsonProperty("athlete_id")
    private final UUID athleteId;

    @JsonProperty("athlete_name")
    private final String athleteName;

    @JsonProperty("athlete_avatar_url")
    private final String athleteAvatarUrl;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("sport_type")
    private final String sportType;

    @JsonProperty("distance_miles")
    private final BigDecimal distanceMiles;

    @JsonProperty("moving_time_secs")
    private final Integer movingTimeSecs;

    @JsonProperty("map_polyline")
    private final String mapPolyline;

    @JsonProperty("avg_pace_display")
    private final String avgPaceDisplay;

    @JsonProperty("kudos_count")
    @Builder.Default
    private final int kudosCount = 0;

    @JsonProperty("comment_count")
    @Builder.Default
    private final int commentCount = 0;

    public static ClubFeedActivityItem from(Activity activity) {
        return builder()
            .createdAt(activity.getStartDate())
            .id(activity.getId())
            .athleteId(activity.getUser().getId())
            .athleteName(activity.getUser().getDisplayName())
            .athleteAvatarUrl(activity.getUser().getProfilePicUrl())
            .name(activity.getName())
            .sportType(activity.getType())
            .distanceMiles(activity.getDistanceMiles())
            .movingTimeSecs(activity.getMovingTimeSeconds())
            .mapPolyline(activity.getMapPolyline())
            .avgPaceDisplay(activity.getAvgPaceDisplay())
            .kudosCount(activity.getKudosCount() == null ? 0 : activity.getKudosCount())
            .commentCount(activity.getCommentCount() == null ? 0 : activity.getCommentCount())
            .build();
    }
}
