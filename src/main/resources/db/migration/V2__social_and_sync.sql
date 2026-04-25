-- Posts (club-scoped discussion posts)
CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    photos TEXT[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_posts_club_id ON posts(club_id);
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);

-- Activity kudos (one per user/activity)
CREATE TABLE activity_kudos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(activity_id, user_id)
);
CREATE INDEX idx_activity_kudos_activity_id ON activity_kudos(activity_id);
CREATE INDEX idx_activity_kudos_user_id ON activity_kudos(user_id);

-- Activity comments
CREATE TABLE activity_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_activity_comments_activity_id ON activity_comments(activity_id);
CREATE INDEX idx_activity_comments_created_at ON activity_comments(created_at);

-- Strava webhook subscription bookkeeping (so we don't double-subscribe)
CREATE TABLE strava_webhook_subscription (
    id INT PRIMARY KEY DEFAULT 1,
    subscription_id BIGINT NOT NULL,
    callback_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (id = 1)
);

-- Bio + profile fields on users
ALTER TABLE users ADD COLUMN bio TEXT;
ALTER TABLE users ADD COLUMN city VARCHAR(100);
ALTER TABLE users ADD COLUMN state VARCHAR(100);

-- Description column on club_goals already covered? Add for context note.
ALTER TABLE club_goals ADD COLUMN description TEXT;
ALTER TABLE club_goals ADD COLUMN created_by_user_id UUID REFERENCES users(id);
