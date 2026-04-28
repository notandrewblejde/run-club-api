-- Multi-source activities: Strava + Apple Health + Health Connect share one table.
-- Strava rows keep strava_id; health rows use import_source/import_external_id with strava_id NULL.

ALTER TABLE activities DROP CONSTRAINT IF EXISTS activities_strava_id_key;

ALTER TABLE activities ADD COLUMN IF NOT EXISTS import_source VARCHAR(32) NOT NULL DEFAULT 'strava';
ALTER TABLE activities ADD COLUMN IF NOT EXISTS import_external_id VARCHAR(128);

UPDATE activities SET import_external_id = strava_id::text WHERE import_external_id IS NULL;

ALTER TABLE activities ALTER COLUMN import_external_id SET NOT NULL;

ALTER TABLE activities ALTER COLUMN strava_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_activities_import_source_external
    ON activities (import_source, import_external_id);
