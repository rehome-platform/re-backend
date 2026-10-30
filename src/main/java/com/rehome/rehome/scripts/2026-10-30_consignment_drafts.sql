-- Nháp ký gửi: chạy dù có dùng ddl-auto=update hay không, vì Hibernate không tự gỡ NOT NULL
-- và không tự sửa CHECK của cột status trên bảng đã tồn tại. PostgreSQL, chạy lại nhiều lần vẫn an toàn.

ALTER TABLE consignment_forms ALTER COLUMN warehouse_id DROP NOT NULL;

DO $$
DECLARE c text;
BEGIN
  FOR c IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'consignment_forms'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%status%'
  LOOP
    EXECUTE format('ALTER TABLE consignment_forms DROP CONSTRAINT %I', c);
  END LOOP;
END $$;

ALTER TABLE consignment_forms ADD CONSTRAINT consignment_forms_status_check
  CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED','INSPECTING'));
