-- Quyên góp món ký gửi (Phần 1 + Phần 2). Chạy lại nhiều lần vẫn an toàn (PostgreSQL).
-- ddl-auto=update không tự sửa CHECK constraint cũ nên phải drop tay.

-- 1) donations.source: DIRECT (ghi nhận tại quầy) | CONSIGNED_ITEM (Member quyên góp món ký gửi)
ALTER TABLE donations ADD COLUMN IF NOT EXISTS source varchar(255) NOT NULL DEFAULT 'DIRECT';
ALTER TABLE donations ADD COLUMN IF NOT EXISTS product_id varchar(255);

UPDATE donations SET source = 'CONSIGNED_ITEM' WHERE product_id IS NOT NULL AND source = 'DIRECT';

DO $$
DECLARE c text;
BEGIN
  FOR c IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'donations'::regclass AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%source%'
  LOOP
    EXECUTE format('ALTER TABLE donations DROP CONSTRAINT %I', c);
  END LOOP;
END $$;
