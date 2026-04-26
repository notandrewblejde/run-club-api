-- Drop the incorrectly named column created by ddl-auto=update (camelCase instead of snake_case)
ALTER TABLE users DROP COLUMN IF EXISTS auth0id;
