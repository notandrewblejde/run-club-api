package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(name = "ClubFeed")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@Builder
public class ClubFeed {

    @JsonProperty("feed")
    private final List<ClubFeedItem> feed;

    @JsonProperty("total")
    private final int total;

    @JsonProperty("page")
    private final int page;

    @JsonProperty("limit")
    private final int limit;
}
