package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateClubRequest {

    @Size(max = 80)
    @JsonProperty("name")
    public String name;

    @Size(max = 1000)
    @JsonProperty("description")
    public String description;

    @Pattern(regexp = "public|private")
    @JsonProperty("privacy_level")
    public String privacyLevel;
}
