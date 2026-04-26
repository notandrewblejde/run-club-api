-- Functional B-tree index supporting case-insensitive prefix search on
-- users.name (e.g. `lower(name) LIKE lower('andr%')`). Replaces the previous
-- in-memory `findAll().stream().filter()` scan in UserController.search.
--
-- Prefix-only is intentional: btree on `lower(name)` is cheap and serves the
-- typing-as-you-search UX directly. If we ever need substring matches we'll
-- add the pg_trgm extension and a GIN index in a follow-up.
CREATE INDEX IF NOT EXISTS idx_users_name_lower ON users (lower(name));
