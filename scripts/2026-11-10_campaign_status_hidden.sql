-- Adds CampaignStatus.HIDDEN (Moderator ẩn chiến dịch do xử lý vi phạm - F-088/Task "Violation
-- Report Management"). Run by hand on Postgres after deploy, same convention as the other scripts
-- in this file (ddl-auto=update never backfills/alters constraints for existing enum columns).
--
-- Investigation note: campaigns.status is mapped by Hibernate via @Enumerated(EnumType.STRING)
-- with no columnDefinition/@Check and no CHECK constraint, and this repo has no schema.sql/Flyway
-- (only ddl-auto=update) - there is currently no DB-level CHECK constraint restricting the values
-- of campaigns.status to alter. Adding CampaignStatus.HIDDEN needs no DDL change: it is stored as
-- a plain varchar and ddl-auto=update will not touch an existing varchar column either way.
--
-- This script is defensive only, for an environment where a CHECK was added by hand outside of
-- Hibernate (not the case in this repo as far as can be verified): it drops any CHECK constraint
-- on campaigns.status so 'HIDDEN' is never rejected, then documents the expected values. Safe to
-- run even when no such constraint exists (the DO block is then a no-op).
DO $$
DECLARE
    found_constraint text;
BEGIN
    SELECT con.conname INTO found_constraint
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'campaigns'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) ILIKE '%status%';

    IF found_constraint IS NOT NULL THEN
        EXECUTE format('ALTER TABLE campaigns DROP CONSTRAINT %I', found_constraint);
    END IF;
END $$;

-- Optional, only if you want the DB to enforce the enum itself going forward (Hibernate does not
-- require this - it already rejects unknown values at the application layer):
-- ALTER TABLE campaigns ADD CONSTRAINT campaigns_status_check CHECK (status IN ('OPEN', 'CLOSED', 'HIDDEN'));
