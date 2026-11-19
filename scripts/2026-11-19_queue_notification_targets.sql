-- Run against the application's database before deploying the new notification targets.
-- Keep enum validation in the application; do not recreate the CHECK.
ALTER TABLE IF EXISTS notifications DROP CONSTRAINT IF EXISTS notifications_target_type_check;

-- One returned order can contain multiple products, each with its own degraded case.
-- Remove only the legacy uniqueness constraint on this foreign key; preserve the FK.
DO $$
DECLARE constraint_row record;
BEGIN
    FOR constraint_row IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.conrelid = to_regclass('public.degraded_item_cases')
          AND c.contype = 'u' AND cardinality(c.conkey) = 1
          AND a.attname = 'return_request_id'
    LOOP
        EXECUTE format('ALTER TABLE public.degraded_item_cases DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;
END $$;
