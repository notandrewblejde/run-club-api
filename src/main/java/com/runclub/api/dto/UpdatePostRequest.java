package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public class UpdatePostRequest {
    @Size(max = 5000)
    @JsonProperty("content")
    public String content;

    @JsonProperty("photos")
    public String[] photos;
}
