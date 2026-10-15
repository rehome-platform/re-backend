-- Run once on an existing PostgreSQL database before starting the updated app.
-- Hibernate ddl-auto=update does not remove the old NOT NULL column.
BEGIN;
ALTER TABLE users DROP COLUMN IF EXISTS username;
COMMIT;
