-- Profile privacy: 'public' (anyone can follow without approval) vs 'private'
-- (follow requests must be approved). Default 'public' so existing users
-- behave the same as before.
ALTER TABLE users
    ADD COLUMN privacy_level VARCHAR(20) NOT NULL DEFAULT 'public'
    CHECK (privacy_level IN ('public', 'private'));

-- A follow can be 'pending' (waiting for the target's approval) or 'accepted'
-- (active follower). Existing rows are accepted retroactively.
ALTER TABLE follows
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'accepted'
    CHECK (status IN ('pending', 'accepted'));
CREATE INDEX idx_follows_status ON follows(status);
