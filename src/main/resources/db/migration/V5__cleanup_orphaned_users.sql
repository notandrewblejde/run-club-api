-- One-time cleanup: delete the user created before the deterministic UUID fix.
-- The row has a random UUID as its id, but Auth.userId() computes a deterministic
-- UUID from the auth0_id. These will never match, causing "user not found" on
-- club creation and other endpoints. Safe to delete — next login recreates correctly.
-- 
-- This deletes ALL users with google-oauth2 auth0_ids since they were all created
-- before the fix. There should only be test users at this point.
DELETE FROM users WHERE auth0_id LIKE 'google-oauth2|%';
DELETE FROM users WHERE auth0_id LIKE 'auth0|%';
