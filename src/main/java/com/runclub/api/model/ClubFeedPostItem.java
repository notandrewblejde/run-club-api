package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "ClubFeedPostItem")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@SuperBuilder
public class ClubFeedPostItem extends ClubFeedItem {

    @JsonProperty("type")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = { "post" })
    @Builder.Default
    private final String type = "post";

    @JsonProperty("id")
    private final UUID id;

    @JsonProperty("author_id")
    private final UUID authorId;

    @JsonProperty("author_name")
    private final String authorName;

    @JsonProperty("author_avatar_url")
    private final String authorAvatarUrl;

    @JsonProperty("content")
    private final String content;

    @JsonProperty("photos")
    private final String[] photos;

    @JsonProperty("related_activity_id")
    private final UUID relatedActivityId;

    @JsonProperty("related_activity_name")
    private final String relatedActivityName;

    @JsonProperty("related_activity_distance_miles")
    private final BigDecimal relatedActivityDistanceMiles;

    @JsonProperty("related_activity_moving_time_secs")
    private final Integer relatedActivityMovingTimeSecs;

    @JsonProperty("related_activity_map_polyline")
    private final String relatedActivityMapPolyline;

    @JsonProperty("updated_at")
    private final LocalDateTime updatedAt;

    public static ClubFeedPostItem from(Post post) {
        var b = builder()
            .createdAt(post.getCreatedAt())
            .id(post.getId())
            .authorId(post.getAuthor().getId())
            .authorName(post.getAuthor().getDisplayName())
            .authorAvatarUrl(post.getAuthor().getProfilePicUrl())
            .content(post.getContent())
            .photos(post.getPhotoUrls())
            .updatedAt(post.getUpdatedAt());

        Activity related = post.getRelatedActivity();
        if (related != null) {
            b.relatedActivityId(related.getId())
                .relatedActivityName(related.getName())
                .relatedActivityDistanceMiles(related.getDistanceMiles())
                .relatedActivityMovingTimeSecs(related.getMovingTimeSeconds())
                .relatedActivityMapPolyline(related.getMapPolyline());
        }
        return b.build();
    }
}
