-- Backfill orders.discount_amount cho các đơn đã tồn tại trước khi có tính năng voucher
-- (gói thành viên VIP + voucher, đặc tả docs/VIP-MEMBERSHIP-SPEC.md §5.1).
--
-- Chạy BẰNG TAY trên Postgres SAU khi deploy bản có cột mới. Vì sao phải chạy tay:
-- `ddl-auto: update` THÊM được cột mới nhưng điền NULL cho mọi dòng cũ, và nó KHÔNG tự chạy
-- UPDATE để điền giá trị mặc định. Entity khai `@ColumnDefault("0")` nên dòng MỚI có 0, còn
-- dòng CŨ vẫn NULL tới khi script này chạy.
--
-- Hệ quả nếu không chạy: mọi chỗ đọc discount_amount của đơn cũ phải tự null-safe. Code hiện tại
-- CÓ null-safe (xem OrderServiceImpl.nullToZero) nên hệ thống không sập, nhưng cột vẫn còn NULL
-- và không thể siết thành NOT NULL - để lâu sẽ có chỗ nào đó quên null-check.
--
-- An toàn để chạy nhiều lần: UPDATE có WHERE ... IS NULL nên lần thứ hai không đụng dòng nào.

BEGIN;

-- 1) Đơn cũ chưa từng có voucher -> phần giảm bằng 0
UPDATE orders
SET discount_amount = 0
WHERE discount_amount IS NULL;

-- 2) Kiểm tra trước khi siết NOT NULL: phải trả về 0 dòng.
--    Nếu còn dòng nào thì có ai đó vừa INSERT trong lúc script chạy - chạy lại bước 1.
DO $$
DECLARE
    remaining bigint;
BEGIN
    SELECT count(*) INTO remaining FROM orders WHERE discount_amount IS NULL;
    IF remaining > 0 THEN
        RAISE EXCEPTION 'Còn % dòng orders.discount_amount IS NULL, chưa siết được NOT NULL', remaining;
    END IF;
END $$;

-- 3) Siết NOT NULL + DEFAULT 0 cho đúng khai báo entity.
--    ddl-auto: update KHÔNG tự làm bước này (nó không đổi nullability của cột đã tồn tại).
ALTER TABLE orders
    ALTER COLUMN discount_amount SET DEFAULT 0;

ALTER TABLE orders
    ALTER COLUMN discount_amount SET NOT NULL;

-- 4) Đối chiếu bất biến total_amount = subtotal + shipping_fee − discount_amount.
--    Chỉ CẢNH BÁO, không ném lỗi: đơn đổi địa chỉ giao hàng trước khi có tính năng voucher có thể
--    lệch vì lý do khác, và script này không được phép chặn deploy vì dữ liệu lịch sử.
DO $$
DECLARE
    mismatched bigint;
BEGIN
    SELECT count(*) INTO mismatched
    FROM orders
    WHERE total_amount <> subtotal + shipping_fee - discount_amount;

    IF mismatched > 0 THEN
        RAISE WARNING 'Có % đơn lệch công thức total_amount = subtotal + shipping_fee - discount_amount, cần soát tay', mismatched;
    END IF;
END $$;

COMMIT;

-- Lưu ý: KHÔNG tạo UNIQUE index trên orders.voucher_id. Một mã voucher áp cho CẢ lần thanh toán
-- và trải trên nhiều đơn (§12.6), nên nhiều dòng orders cùng trỏ một voucher_id là đúng. Đây là
-- cùng loại bẫy với scripts/drop-payment-gateway-ref-unique.sql.
--
-- products.featured_until KHÔNG cần script: cột nullable, ddl-auto: update thêm được và NULL
-- chính là "không nổi bật".
