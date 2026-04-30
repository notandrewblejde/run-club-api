package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.runclub.api.entity.UserNotification;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(name = "Notification")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@Getter
@Builder
public class Notification {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("type")
    private final String type;

    @JsonProperty("title")
    private final String title;

    @JsonProperty("body")
    private final String body;

    @JsonProperty("payload_json")
    private final String payloadJson;

    @JsonProperty("related_activity_id")
    private final String relatedActivityId;

    @JsonProperty("read_at")
    private final String readAt;

    @JsonProperty("created_at")
    private final String createdAt;

    public static Notification from(UserNotification n) {
        return Notification.builder()
            .id(n.getId().toString())
            .type(n.getType())
            .title(n.getTitle())
            .body(n.getBody())
            .payloadJson(n.getPayloadJson())
            .relatedActivityId(n.getRelatedActivityId() != null ? n.getRelatedActivityId().toString() : null)
            .readAt(n.getReadAt() != null ? n.getReadAt().toString() : null)
            .createdAt(n.getCreatedAt().toString())
            .build();
    }
}
