-- Dọn cột pdf_url sau khi PDF biên nhận (F-022) chuyển sang bảng consignment_receipt_pdfs.
--
-- ddl-auto: update CHỈ thêm bảng/cột mới, không bao giờ drop cột cũ, nên bảng
-- consignment_receipts sẽ còn pdf_url treo lại sau khi deploy. Code không đọc nó nữa.
--
-- Chạy TAY, sau khi đã deploy bản mới và xác nhận chạy ổn. Không có cách lấy lại
-- dữ liệu cột này sau khi drop, nên kiểm tra trước xem còn gì đáng giữ không.

-- 1) Xem còn bao nhiêu dòng có pdf_url và đã có PDF trong bảng mới chưa
SELECT
    count(*)                                             AS total_receipts,
    count(r.pdf_url)                                     AS still_has_old_url,
    count(*) FILTER (WHERE r.confirmed_at IS NOT NULL)   AS confirmed,
    count(p.receipt_id)                                  AS has_new_pdf
FROM consignment_receipts r
LEFT JOIN consignment_receipt_pdfs p ON p.receipt_id = r.id;

-- 2) Những biên nhận đã xác nhận nhưng CHƯA có PDF trong bảng mới.
--    Đây là dữ liệu cũ: PDF nằm ở storage ngoài theo pdf_url, không migrate tự động được.
--    Nếu cần giữ, tải file theo pdf_url rồi insert vào consignment_receipt_pdfs trước khi drop.
SELECT r.id, r.receipt_no, r.confirmed_at, r.pdf_url
FROM consignment_receipts r
LEFT JOIN consignment_receipt_pdfs p ON p.receipt_id = r.id
WHERE r.confirmed_at IS NOT NULL
  AND p.receipt_id IS NULL;

-- 3) Drop cột
ALTER TABLE consignment_receipts DROP COLUMN IF EXISTS pdf_url;
