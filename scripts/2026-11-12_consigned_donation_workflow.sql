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







COMMIT;
