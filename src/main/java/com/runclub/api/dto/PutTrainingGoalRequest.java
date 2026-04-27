package com.runclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public class PutTrainingGoalRequest {

    @Size(max = 8000)
    @JsonProperty("goal_text")
    private String goalText;

    public String getGoalText() {
        return goalText;
    }

    public void setGoalText(String goalText) {
        this.goalText = goalText;
    }
}
