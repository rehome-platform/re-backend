-- Removes list-based CHECK constraints on tables in public. Review the affected constraints before deployment.
BEGIN;

DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT ns.nspname AS schema_name, rel.relname AS tbl, con.conname AS cname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace ns ON ns.oid = rel.relnamespace
    WHERE con.contype = 'c' AND ns.nspname = 'public'
      AND (pg_get_constraintdef(con.oid) ILIKE '%ANY (ARRAY%'
           OR pg_get_constraintdef(con.oid) ILIKE '% IN (%')
  LOOP
    EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I', r.schema_name, r.tbl, r.cname);
  END LOOP;
END $$;

ALTER TABLE public.item_withdrawal_requests ALTER COLUMN pickup_deadline DROP NOT NULL;
COMMIT;
