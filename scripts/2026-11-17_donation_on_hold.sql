BEGIN;

ALTER TABLE donations DROP CONSTRAINT IF EXISTS donations_status_check;
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE queue_items DROP CONSTRAINT IF EXISTS queue_items_type_check;

ALTER TABLE donations
    ADD COLUMN IF NOT EXISTS held_at timestamptz,
    ADD COLUMN IF NOT EXISTS hold_reason text,
    ADD COLUMN IF NOT EXISTS reassigned_from_campaign_id uuid REFERENCES campaigns(id),
    ADD COLUMN IF NOT EXISTS reassigned_at timestamptz,
    ADD COLUMN IF NOT EXISTS reassigned_by uuid REFERENCES accounts(id);

ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS status_before_organization_ban varchar(255);

UPDATE donations
SET status = 'ON_HOLD',
    held_at = COALESCE(held_at, CURRENT_TIMESTAMP),
    hold_reason = COALESCE(hold_reason, 'Tổ chức đã bị khoá')
WHERE status = 'HELD_FOR_TRANSFER';

INSERT INTO queue_items (id, type, ref_id, warehouse_id, created_at)
SELECT gen_random_uuid()::text, 'DONATION_REASSIGN', id::text, warehouse_id,
       COALESCE(held_at, CURRENT_TIMESTAMP)
FROM donations
WHERE status = 'ON_HOLD'
ON CONFLICT (type, ref_id) DO NOTHING;

COMMIT;
