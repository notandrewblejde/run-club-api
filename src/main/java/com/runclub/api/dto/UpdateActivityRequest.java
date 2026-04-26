package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Partial update of app-owned activity fields. Omitted JSON keys leave the field unchanged.
 */
public class UpdateActivityRequest {
    @JsonProperty("user_note")
    public String userNote;

    @JsonProperty("app_photos")
    public String[] appPhotos;
}
