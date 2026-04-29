-- Device push tokens (Expo managed — works for both iOS/Android)
CREATE TABLE user_push_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NOT NULL DEFAULT 'expo', -- expo, apns, fcm
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, token)
);
CREATE INDEX idx_push_tokens_user ON user_push_tokens(user_id);

-- Per-user notification preferences
CREATE TABLE user_notification_prefs (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    club_activity_alerts BOOLEAN NOT NULL DEFAULT true,  -- member ran a run
    daily_coach_tip BOOLEAN NOT NULL DEFAULT true,       -- 8am AI coach brief
    goal_progress BOOLEAN NOT NULL DEFAULT true,         -- club goal milestone
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Debounce tracking: last push sent per (user, type) window
CREATE TABLE push_debounce (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_type VARCHAR(100) NOT NULL,
    last_sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, notification_type)
);

-- Extend user_notifications for push delivery status
ALTER TABLE user_notifications ADD COLUMN IF NOT EXISTS push_sent_at TIMESTAMPTZ;
ALTER TABLE user_notifications ADD COLUMN IF NOT EXISTS push_error TEXT;
