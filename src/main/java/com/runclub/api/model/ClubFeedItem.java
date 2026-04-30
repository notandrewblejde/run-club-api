package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Mixed club home feed row: a club post or a member activity snapshot.
 * Serialized with {@code type} as the discriminator for clients and OpenAPI.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ClubFeedPostItem.class, name = "post"),
    @JsonSubTypes.Type(value = ClubFeedActivityItem.class, name = "activity")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@SuperBuilder
@Schema(
    name = "ClubFeedItem",
    description = "Union of post and activity rows; inspect `type` to narrow.",
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "post", schema = ClubFeedPostItem.class),
        @DiscriminatorMapping(value = "activity", schema = ClubFeedActivityItem.class)
    }
)
public abstract class ClubFeedItem {

    @JsonProperty("created_at")
    @Schema(description = "Post created time, or activity start time for activity rows.")
    private final LocalDateTime createdAt;
}
