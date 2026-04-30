package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Small Stripe-style API objects (named for what they are, not {@code *Response}).
 */
public final class JsonDtos {

    private JsonDtos() {}

    @Schema(name = "ActivitySummary")
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    @Getter
    @Builder
    public static final class ActivitySummary {
        @JsonProperty("activity_id")
        private final UUID activityId;
        @JsonProperty("summary")
        private final String summary;
    }

    @Schema(name = "CoachReply")
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    @Getter
    @Builder
    public static final class CoachReply {
        @JsonProperty("reply")
        private final String reply;
    }

    @Schema(name = "HealthImportResult")
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    @Getter
    @Builder
    public static final class HealthImportResult {
        @JsonProperty("imported")
        private final int imported;
        @JsonProperty("skipped")
        private final int skipped;
    }

    @Schema(name = "NotificationPreview")
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    @Getter
    @Builder
    public static final class NotificationPreview {
        @JsonProperty("unread_count")
        private final long unreadCount;
        @JsonProperty("latest")
        private final Notification latest;
    }

    @Schema(name = "NotificationReadAllResult")
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    @Getter
    @Builder
    public static final class NotificationReadAllResult {
        @JsonProperty("updated")
        private final int updated;
    }

    @Schema(name = "GoalFeedbackDeletion")
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    @Getter
    @Builder
    public static final class GoalFeedbackDeletion {
        @JsonProperty("deleted")
        private final int deleted;
    }
}
