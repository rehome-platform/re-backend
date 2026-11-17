BEGIN;
ALTER TABLE consignment_receipts
    ADD COLUMN IF NOT EXISTS status varchar(255),
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS edit_reason text,
    ADD COLUMN IF NOT EXISTS edited_at timestamptz,
    ADD COLUMN IF NOT EXISTS void_reason text,
    ADD COLUMN IF NOT EXISTS voided_at timestamptz,
    ADD COLUMN IF NOT EXISTS voided_by uuid REFERENCES accounts(id),
    ADD COLUMN IF NOT EXISTS previous_receipt_id uuid REFERENCES consignment_receipts(id);
UPDATE consignment_receipts
SET status = CASE WHEN confirmed_at IS NULL THEN 'PENDING' ELSE 'CONFIRMED' END
WHERE status IS NULL;
ALTER TABLE consignment_receipts ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE consignment_receipts ALTER COLUMN status SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_receipt_replacement ON consignment_receipts(previous_receipt_id)
WHERE previous_receipt_id IS NOT NULL;
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT conrelid::regclass AS tbl, conname
        FROM pg_constraint
        WHERE contype = 'c' AND conrelid IN ('consignment_receipts'::regclass, 'notifications'::regclass)
          AND (pg_get_constraintdef(oid) ILIKE '%ANY (ARRAY%' OR pg_get_constraintdef(oid) ILIKE '% IN (%')
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END $$;
COMMIT;
