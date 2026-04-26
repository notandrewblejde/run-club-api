package com.runclub.api.dto;

import jakarta.validation.constraints.Size;

public class PutTrainingGoalRequest {

    @Size(max = 8000)
    private String goal_text;

    public String getGoal_text() {
        return goal_text;
    }

    public void setGoal_text(String goal_text) {
        this.goal_text = goal_text;
    }
}
