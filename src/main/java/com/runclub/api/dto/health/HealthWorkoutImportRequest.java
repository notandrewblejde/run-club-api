package com.runclub.api.dto.health;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class HealthWorkoutImportRequest {

    @NotNull
    @Size(max = 50)
    @Valid
    @JsonProperty("workouts")
    private List<HealthWorkoutImportItem> workouts;

    public List<HealthWorkoutImportItem> getWorkouts() {
        return workouts;
    }

    public void setWorkouts(List<HealthWorkoutImportItem> workouts) {
        this.workouts = workouts;
    }
}
