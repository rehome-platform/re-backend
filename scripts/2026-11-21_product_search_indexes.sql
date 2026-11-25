-- Optional search tuning, separate from the mandatory try-on schema migration.
-- Enabling pg_trgm may require the database owner.
-- Run during maintenance: ordinary CREATE INDEX can block writes while building.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_products_sale_name_trgm
    ON products USING gin (name_normalized gin_trgm_ops) WHERE status = 'ON_SALE';
