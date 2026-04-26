-- Restore join timestamp for product/analytics. Goal mileage is not capped by
-- this date; see GoalAttributionService backfill on join.
ALTER TABLE club_memberships
    ADD COLUMN IF NOT EXISTS joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
