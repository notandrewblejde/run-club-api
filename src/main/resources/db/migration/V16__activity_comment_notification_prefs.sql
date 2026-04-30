ALTER TABLE user_notification_prefs
    ADD COLUMN IF NOT EXISTS activity_comment_alerts BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN user_notification_prefs.activity_comment_alerts IS 'Push when someone comments on your activity';
