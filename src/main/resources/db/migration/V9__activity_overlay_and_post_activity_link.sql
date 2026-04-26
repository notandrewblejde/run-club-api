-- App-owned activity overlay (not overwritten by Strava sync)
ALTER TABLE activities ADD COLUMN user_note TEXT;
ALTER TABLE activities ADD COLUMN app_photos TEXT[];

-- Optional link from a club post to a member activity (e.g. "share run")
ALTER TABLE posts ADD COLUMN related_activity_id UUID REFERENCES activities(id) ON DELETE SET NULL;
CREATE INDEX idx_posts_related_activity_id ON posts(related_activity_id);
