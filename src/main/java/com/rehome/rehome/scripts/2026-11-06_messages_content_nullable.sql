-- Chạy nếu KHÔNG dùng ddl-auto=update (PostgreSQL). ddl-auto=update không tự drop NOT NULL khi
-- entity đổi sang nullable, nên phải chạy tay. Chạy lại nhiều lần vẫn an toàn.
-- messages.content giờ cho phép null: tin nhắn chỉ gửi ảnh (imageUrls không rỗng) không cần content.
ALTER TABLE messages ALTER COLUMN content DROP NOT NULL;
