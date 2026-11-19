BEGIN;
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS ghn_fallback_used boolean DEFAULT false;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS handed_over_at timestamptz;
-- PACKED is a new enum value. Remove enum checks without recreating them.
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT conrelid::regclass AS tbl, conname FROM pg_constraint
        WHERE contype = 'c' AND conrelid = 'orders'::regclass
          AND (pg_get_constraintdef(oid) ILIKE '%ANY (ARRAY%' OR pg_get_constraintdef(oid) ILIKE '% IN (%')
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END $$;
COMMIT;
