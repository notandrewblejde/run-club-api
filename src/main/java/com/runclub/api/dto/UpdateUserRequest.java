package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public class UpdateUserRequest {
    @Size(max = 80)
    @JsonProperty("name")
    public String name;

    @Size(max = 500)
    @JsonProperty("bio")
    public String bio;

    @Size(max = 100)
    @JsonProperty("city")
    public String city;

    @Size(max = 100)
    @JsonProperty("state")
    public String state;

    @JsonProperty("avatar_url")
    public String avatarUrl;
}
