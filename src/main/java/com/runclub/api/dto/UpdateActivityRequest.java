package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Partial update of app-owned activity fields. Omitted JSON keys leave the field unchanged.
 */
public class UpdateActivityRequest {
    @Size(max = 5000)
    @JsonProperty("user_note")
    public String userNote;

    @JsonProperty("app_photos")
    public String[] appPhotos;
}
