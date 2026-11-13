BEGIN;

ALTER TABLE products ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE end_of_term_decisions ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE degraded_item_cases ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE degraded_item_cases ADD COLUMN IF NOT EXISTS item_donated boolean NOT NULL DEFAULT false;
ALTER TABLE item_withdrawal_requests ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE notifications ALTER COLUMN target_id TYPE varchar(255) USING target_id::text;
ALTER TABLE donations ADD COLUMN IF NOT EXISTS product_id varchar(255) REFERENCES products(id);
ALTER TABLE donations ADD COLUMN IF NOT EXISTS source varchar(255) NOT NULL DEFAULT 'DIRECT';

-- Drop Hibernate's previous enum checks even if the generated constraint names differ.
DO $$
DECLARE
    checked_column record;
    constraint_name text;
BEGIN
    FOR checked_column IN
        SELECT * FROM (VALUES
            ('item_withdrawal_requests'::regclass, 'status'),
            ('end_of_term_decisions'::regclass, 'decision'),
            ('queue_items'::regclass, 'type'),
            ('notifications'::regclass, 'type'),
            ('notifications'::regclass, 'target_type'),
            ('donations'::regclass, 'source')
        ) AS columns(table_id, column_name)
    LOOP
        FOR constraint_name IN
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND c.conkey = ARRAY[a.attnum]
            WHERE c.conrelid = checked_column.table_id AND c.contype = 'c'
              AND a.attname = checked_column.column_name
              AND pg_get_constraintdef(c.oid) LIKE '%ANY (%'
        LOOP
            EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', checked_column.table_id, constraint_name);
        END LOOP;
    END LOOP;
END $$;

UPDATE donations SET source = 'CONSIGNED_ITEM' WHERE product_id IS NOT NULL AND source = 'DIRECT';

ALTER TABLE item_withdrawal_requests ADD CONSTRAINT item_withdrawal_requests_status_check
    CHECK (status IN ('PENDING_APPROVAL', 'PENDING_PICKUP', 'REJECTED', 'COMPLETED', 'EXPIRED', 'CANCELLED', 'CONVERTED_TO_DONATION'));

ALTER TABLE end_of_term_decisions ADD CONSTRAINT end_of_term_decisions_decision_check
    CHECK (decision IN ('TAKE_BACK', 'RE_CONSIGN', 'UNCLAIMED', 'DONATE'));

ALTER TABLE queue_items ADD CONSTRAINT queue_items_type_check
    CHECK (type IN ('FORM_REVIEW', 'INSPECTION', 'PACKING', 'RETURN', 'ORG_VERIFICATION', 'LEGAL_CHANGE', 'VIOLATION_REPORT', 'WITHDRAWAL', 'DONATION_PICKUP', 'DONATION_PREP'));

ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('ORDER_UPDATE', 'WALLET_EVENT', 'TERM_EXPIRY', 'VERIFICATION_RESULT', 'QUEUE_TASK', 'DONATION_RECORDED', 'DONATION_HANDED_OVER', 'COMMENT_REPLIED', 'POST_COMMENTED', 'FOLLOWED_ORG_CAMPAIGN', 'FOLLOWED_ORG_POST', 'VIOLATION_WARNING', 'VIOLATION_REPORT_HANDLED'));

ALTER TABLE notifications ADD CONSTRAINT notifications_target_type_check
    CHECK (target_type IN ('CAMPAIGN', 'POST', 'ORGANIZATION', 'LEGAL_CHANGE', 'DEGRADED_ITEM_CASE', 'PRODUCT'));

ALTER TABLE donations ADD CONSTRAINT donations_source_check
    CHECK (source IN ('DIRECT', 'CONSIGNED_ITEM'));

COMMIT;
