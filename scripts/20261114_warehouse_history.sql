BEGIN;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipment_created_at timestamptz;
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS warehouse_id uuid,
    ADD COLUMN IF NOT EXISTS activity_type varchar(32),
    ADD COLUMN IF NOT EXISTS audit_group varchar(32),
    ADD COLUMN IF NOT EXISTS result varchar(255);
CREATE INDEX IF NOT EXISTS idx_audit_warehouse_activity_time_id
    ON audit_logs (warehouse_id, activity_type, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_warehouse_group_time_id
    ON audit_logs (warehouse_id, audit_group, created_at DESC, id DESC);
COMMIT;
