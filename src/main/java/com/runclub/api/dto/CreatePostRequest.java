package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class CreatePostRequest {
    @NotBlank
    @Size(max = 5000)
    @JsonProperty("content")
    public String content;

    @JsonProperty("photos")
    public String[] photos;

    /** When set, post is linked to this activity (must belong to the author). */
    @JsonProperty("related_activity_id")
    public UUID relatedActivityId;
}
