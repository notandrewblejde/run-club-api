-- Optional conversation refining the training goal (stored server-side; included in interpretation prompts).
CREATE TABLE user_training_goal_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_goal_feedback_role CHECK (role IN ('user', 'assistant'))
);

CREATE INDEX idx_goal_feedback_user_created ON user_training_goal_feedback (user_id, created_at DESC);
