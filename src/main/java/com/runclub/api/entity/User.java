package com.runclub.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    /**
     * Deterministic UUID derived from the Auth0 `sub` claim (see UserController.getMe).
     * Set explicitly on first lazy-create; never auto-generated, since every other
     * endpoint resolves the row by recomputing this UUID from the JWT.
     */
    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String auth0Id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "name")
    private String displayName;

    @Column(name = "avatar_url")
    private String profilePicUrl;

    @Column(name = "strava_id")
    private Long stravaAthleteId;

    @Column(name = "strava_access_token")
    private String stravaAccessToken;

    @Column(name = "strava_refresh_token")
    private String stravaRefreshToken;

    @Column(name = "strava_token_expires_at")
    private LocalDateTime stravaTokenExpiresAt;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "privacy_level", nullable = false)
    private String privacyLevel = "public"; // "public" | "private"

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getAuth0Id() { return auth0Id; }
    public void setAuth0Id(String auth0Id) { this.auth0Id = auth0Id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getProfilePicUrl() { return profilePicUrl; }
    public void setProfilePicUrl(String profilePicUrl) { this.profilePicUrl = profilePicUrl; }

    public Long getStravaAthleteId() { return stravaAthleteId; }
    public void setStravaAthleteId(Long stravaAthleteId) { this.stravaAthleteId = stravaAthleteId; }

    public String getStravaAccessToken() { return stravaAccessToken; }
    public void setStravaAccessToken(String stravaAccessToken) { this.stravaAccessToken = stravaAccessToken; }

    public String getStravaRefreshToken() { return stravaRefreshToken; }
    public void setStravaRefreshToken(String stravaRefreshToken) { this.stravaRefreshToken = stravaRefreshToken; }

    public LocalDateTime getStravaTokenExpiresAt() { return stravaTokenExpiresAt; }
    public void setStravaTokenExpiresAt(LocalDateTime stravaTokenExpiresAt) { this.stravaTokenExpiresAt = stravaTokenExpiresAt; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPrivacyLevel() { return privacyLevel; }
    public void setPrivacyLevel(String privacyLevel) { this.privacyLevel = privacyLevel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
