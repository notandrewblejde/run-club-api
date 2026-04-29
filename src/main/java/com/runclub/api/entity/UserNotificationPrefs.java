package com.runclub.api.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_notification_prefs")
public class UserNotificationPrefs {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "club_activity_alerts") private boolean clubActivityAlerts = true;
    @Column(name = "daily_coach_tip") private boolean dailyCoachTip = true;
    @Column(name = "goal_progress") private boolean goalProgress = true;
    @Column(name = "updated_at") private Instant updatedAt = Instant.now();

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public boolean isClubActivityAlerts() { return clubActivityAlerts; }
    public void setClubActivityAlerts(boolean v) { this.clubActivityAlerts = v; }
    public boolean isDailyCoachTip() { return dailyCoachTip; }
    public void setDailyCoachTip(boolean v) { this.dailyCoachTip = v; }
    public boolean isGoalProgress() { return goalProgress; }
    public void setGoalProgress(boolean v) { this.goalProgress = v; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
