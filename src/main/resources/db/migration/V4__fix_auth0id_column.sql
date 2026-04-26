-- Drop the incorrectly named column created by ddl-auto=update
ALTER TABLE users DROP COLUMN IF EXISTS auth0id;

-- Ensure auth0_id exists with correct constraints (it should from V1, but just in case)
ALTER TABLE users ALTER COLUMN auth0_id SET NOT NULL;
