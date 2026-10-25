-- Bỏ ràng buộc UNIQUE trên payments.momo_order_id và payments.vnp_txn_ref.
--
-- Từ khi hỗ trợ trả gộp nhiều đơn bằng một giao dịch MoMo/VNPay (giỏ có món ở 2 kho bị tách
-- thành 2 đơn nhưng người mua chỉ quét QR một lần), mỗi đơn vẫn giữ một dòng payments riêng
-- để đối soát, nhưng cả lô dùng CHUNG một momo_order_id / vnp_txn_ref. UNIQUE cũ sẽ làm
-- INSERT dòng thứ hai của cùng một giao dịch văng lỗi duplicate key.
--
-- ddl-auto: update chỉ thêm bảng/cột mới, KHÔNG bao giờ drop constraint cũ, nên bỏ
-- unique = true trong entity Payment không tự gỡ được index đã tạo trên DB thật.
--
-- Chạy TAY trên Postgres, SAU khi deploy bản mới. An toàn và đảo ngược được (tạo lại
-- index là xong), nhưng chỉ chạy khi đã chắc không còn bản cũ nào đang ghi vào bảng này.

-- 1) Xem hiện có những ràng buộc/index unique nào trên hai cột đó
SELECT i.relname AS index_name,
       idx.indisunique AS is_unique,
       pg_get_indexdef(idx.indexrelid) AS definition
FROM pg_index idx
JOIN pg_class t ON t.oid = idx.indrelid
JOIN pg_class i ON i.oid = idx.indexrelid
JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY (idx.indkey)
WHERE t.relname = 'payments'
  AND idx.indisunique
  AND a.attname IN ('momo_order_id', 'vnp_txn_ref');

-- 2) Kiểm tra dữ liệu hiện tại không có gì bất thường trước khi gỡ
--    (trước khi có tính năng trả gộp thì mỗi mã giao dịch chỉ nên ứng với đúng 1 đơn)
SELECT momo_order_id, count(*) AS rows
FROM payments
WHERE momo_order_id IS NOT NULL
GROUP BY momo_order_id
HAVING count(*) > 1;

SELECT vnp_txn_ref, count(*) AS rows
FROM payments
WHERE vnp_txn_ref IS NOT NULL
GROUP BY vnp_txn_ref
HAVING count(*) > 1;

-- 3) Gỡ ràng buộc. Hibernate đặt tên constraint dạng ngẫu nhiên (UK...), nên gỡ theo tên
--    tìm được ở bước 1. Hai lệnh dưới xử lý cả trường hợp là table constraint lẫn index rời.
DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY (c.conkey)
        WHERE t.relname = 'payments'
          AND c.contype = 'u'
          AND a.attname IN ('momo_order_id', 'vnp_txn_ref')
    LOOP
        EXECUTE format('ALTER TABLE payments DROP CONSTRAINT %I', constraint_name);
        RAISE NOTICE 'Da go constraint %', constraint_name;
    END LOOP;
END $$;

DO $$
DECLARE
    index_name text;
BEGIN
    FOR index_name IN
        SELECT i.relname
        FROM pg_index idx
        JOIN pg_class t ON t.oid = idx.indrelid
        JOIN pg_class i ON i.oid = idx.indexrelid
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY (idx.indkey)
        WHERE t.relname = 'payments'
          AND idx.indisunique
          AND a.attname IN ('momo_order_id', 'vnp_txn_ref')
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', index_name);
        RAISE NOTICE 'Da go index %', index_name;
    END LOOP;
END $$;

-- 4) Tra cứu IPN đi theo hai cột này nên vẫn cần index, chỉ là không unique nữa
CREATE INDEX IF NOT EXISTS idx_payments_momo_order_id ON payments (momo_order_id);
CREATE INDEX IF NOT EXISTS idx_payments_vnp_txn_ref ON payments (vnp_txn_ref);
