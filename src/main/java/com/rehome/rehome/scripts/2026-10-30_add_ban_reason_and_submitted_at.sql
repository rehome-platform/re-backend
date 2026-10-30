-- Chạy nếu KHÔNG dùng ddl-auto=update (PostgreSQL). Chạy lại nhiều lần vẫn an toàn.
ALTER TABLE accounts              ADD COLUMN IF NOT EXISTS ban_reason   text;
ALTER TABLE organization_profiles ADD COLUMN IF NOT EXISTS submitted_at timestamp(6) with time zone;
