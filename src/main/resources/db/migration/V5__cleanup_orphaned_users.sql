-- One-time cleanup: delete users created with random UUIDs before the
-- deterministic UUID fix. These rows have auth0_id set but their id field
-- doesn't match UUID.nameUUIDFromBytes(auth0_id). Safe to delete since
-- next login will recreate the row with the correct deterministic UUID.
DELETE FROM users
WHERE auth0_id LIKE 'google-oauth2|%'
  AND id != (
    CASE WHEN length(auth0_id) > 0
    THEN md5(auth0_id)::uuid
    ELSE id END
  );
