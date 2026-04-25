-- Initial schema for Run Club API

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth0_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    avatar_url TEXT,
    strava_id BIGINT UNIQUE,
    strava_access_token TEXT,
    strava_refresh_token TEXT,
    strava_token_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    strava_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    sport_type VARCHAR(50),
    start_date TIMESTAMPTZ NOT NULL,
    city VARCHAR(100),
    state VARCHAR(100),
    distance_meters DECIMAL(10, 2),
    moving_time_secs INTEGER,
    elapsed_time_secs INTEGER,
    elevation_gain_ft DECIMAL(10, 2),
    max_elevation_ft DECIMAL(10, 2),
    avg_heart_rate_bpm INTEGER,
    max_heart_rate_bpm INTEGER,
    map_polyline TEXT,
    photos TEXT[],
    is_personal_record BOOLEAN DEFAULT FALSE,
    kudos_count INTEGER DEFAULT 0,
    comment_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(strava_id)
);

CREATE TABLE clubs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    privacy_level VARCHAR(50) DEFAULT 'private',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE club_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) DEFAULT 'member',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(club_id, user_id)
);

CREATE TABLE follows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(follower_id, following_id),
    CHECK (follower_id != following_id)
);

CREATE TABLE club_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    target_distance_miles DECIMAL(10, 2),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE goal_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES club_goals(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_id UUID REFERENCES activities(id),
    distance_miles DECIMAL(10, 2),
    contributed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(goal_id, activity_id)
);

-- Indexes for common queries
CREATE INDEX idx_activities_user_id ON activities(user_id);
CREATE INDEX idx_activities_start_date ON activities(start_date);
CREATE INDEX idx_club_memberships_club_id ON club_memberships(club_id);
CREATE INDEX idx_club_memberships_user_id ON club_memberships(user_id);
CREATE INDEX idx_follows_follower_id ON follows(follower_id);
CREATE INDEX idx_follows_following_id ON follows(following_id);
CREATE INDEX idx_goal_contributions_goal_id ON goal_contributions(goal_id);
CREATE INDEX idx_goal_contributions_user_id ON goal_contributions(user_id);
