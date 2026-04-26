-- Personal training goal (natural language) + LLM interpretation cache
CREATE TABLE user_training_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    goal_text TEXT NOT NULL DEFAULT '',
    interpretation_json TEXT,
    interpretation_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One cached daily plan per user per calendar day (UTC in v1)
CREATE TABLE user_daily_training_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_date DATE NOT NULL,
    headline TEXT NOT NULL,
    body_json TEXT NOT NULL,
    inputs_hash VARCHAR(64),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, plan_date)
);

CREATE INDEX idx_daily_plans_user_date ON user_daily_training_plans (user_id, plan_date DESC);

-- In-app notifications (e.g. post–Strava sync celebration + recovery tips)
CREATE TABLE user_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    payload_json TEXT,
    related_activity_id UUID REFERENCES activities(id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_created ON user_notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_read ON user_notifications (user_id, read_at);

-- At most one ACTIVITY_ARRIVED row per user per activity (webhook create/update dedupe)
CREATE UNIQUE INDEX uq_notification_activity_arrived
    ON user_notifications (user_id, related_activity_id)
    WHERE type = 'ACTIVITY_ARRIVED' AND related_activity_id IS NOT NULL;
