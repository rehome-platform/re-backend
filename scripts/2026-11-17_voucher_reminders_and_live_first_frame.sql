BEGIN;

ALTER TABLE member_vouchers ADD COLUMN IF NOT EXISTS reminded_at timestamptz;

-- Rename the original default template only. Preserve customized templates.
UPDATE voucher_templates
SET name = 'Giảm 15.000đ phí vận chuyển'
WHERE type = 'SHIPPING' AND name = 'Miễn phí vận chuyển'
  AND NOT EXISTS (
      SELECT 1 FROM voucher_templates WHERE name = 'Giảm 15.000đ phí vận chuyển'
  );

-- Sessions created before this column existed were already counted.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = 'live_tryon_sessions'::regclass
          AND attname = 'first_frame_at' AND NOT attisdropped
    ) THEN
        ALTER TABLE live_tryon_sessions ADD COLUMN first_frame_at timestamptz;
        UPDATE live_tryon_sessions SET first_frame_at = created_at;
    END IF;
END $$;

COMMIT;
