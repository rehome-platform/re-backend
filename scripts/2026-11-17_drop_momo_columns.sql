-- MoMo sandbox không còn hỗ trợ -> bỏ hẳn tích hợp MoMo, chỉ còn VNPay.
-- `ddl-auto: update` KHÔNG drop cột đã tồn tại, nên các cột momo_* còn sót lại trên Postgres
-- sau khi deploy bản đã gỡ code MoMo. Chạy BẰNG TAY trên Postgres sau deploy.
--
-- Giá trị enum MOMO cũng đã bị xoá khỏi OrderPaymentMethod / WithdrawalFeePaymentMethod (code).
-- Nếu còn dòng dữ liệu cũ mang giá trị 'MOMO' ở các cột enum-backed (orders.payment_method,
-- payments.method, item_withdrawal_requests.fee_payment_method, membership_payments.method),
-- Hibernate sẽ ném IllegalArgumentException khi đọc lại dòng đó -> phải chuyển các dòng này
-- sang 'VNPAY' TRƯỚC khi deploy bản đã xoá giá trị enum MOMO.

BEGIN;

-- 1) Dữ liệu lịch sử: coi các giao dịch MOMO cũ là đã xử lý qua cổng (đổi nhãn sang VNPAY để khớp
--    enum còn lại). Không đổi được thành dữ liệu "đúng" hồi tố - chỉ để ứng dụng đọc lại không vỡ.
UPDATE orders SET payment_method = 'VNPAY' WHERE payment_method = 'MOMO';
UPDATE payments SET method = 'VNPAY' WHERE method = 'MOMO';
UPDATE item_withdrawal_requests SET fee_payment_method = 'VNPAY' WHERE fee_payment_method = 'MOMO';
UPDATE membership_payments SET method = 'VNPAY' WHERE method = 'MOMO';

-- 2) Xoá cột momo_* không còn dùng. DROP COLUMN tự dọn index phụ thuộc
--    (ví dụ idx_membership_payments_momo_order_id).
ALTER TABLE payments
    DROP COLUMN IF EXISTS momo_order_id,
    DROP COLUMN IF EXISTS momo_request_id,
    DROP COLUMN IF EXISTS momo_transaction_id;

ALTER TABLE item_withdrawal_requests
    DROP COLUMN IF EXISTS momo_order_id,
    DROP COLUMN IF EXISTS momo_request_id;

ALTER TABLE membership_payments
    DROP COLUMN IF EXISTS momo_order_id,
    DROP COLUMN IF EXISTS momo_request_id,
    DROP COLUMN IF EXISTS momo_transaction_id;

-- 3) Payment.method mặc định đổi từ 'MOMO' sang 'VNPAY' (xem entity Payment).
ALTER TABLE payments ALTER COLUMN method SET DEFAULT 'VNPAY';

COMMIT;
