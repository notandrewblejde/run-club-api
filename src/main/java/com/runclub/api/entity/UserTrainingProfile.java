package com.runclub.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_training_profiles")
public class UserTrainingProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "goal_text", nullable = false, columnDefinition = "TEXT")
    private String goalText = "";

    @Column(name = "interpretation_json", columnDefinition = "TEXT")
    private String interpretationJson;

    @Column(name = "interpretation_updated_at")
    private LocalDateTime interpretationUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getGoalText() {
        return goalText;
    }

    public void setGoalText(String goalText) {
        this.goalText = goalText;
    }

    public String getInterpretationJson() {
        return interpretationJson;
    }

    public void setInterpretationJson(String interpretationJson) {
        this.interpretationJson = interpretationJson;
    }

    public LocalDateTime getInterpretationUpdatedAt() {
        return interpretationUpdatedAt;
    }

    public void setInterpretationUpdatedAt(LocalDateTime interpretationUpdatedAt) {
        this.interpretationUpdatedAt = interpretationUpdatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
