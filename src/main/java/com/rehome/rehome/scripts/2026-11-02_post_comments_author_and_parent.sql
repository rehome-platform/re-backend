-- Chạy nếu KHÔNG dùng ddl-auto=update (PostgreSQL). Chạy lại nhiều lần vẫn an toàn (dùng IF NOT EXISTS,
-- và RENAME chỉ thực hiện khi cột cũ còn tồn tại).
-- post_comments.member_id -> author_id: cho phép cả ORGANIZATION trả lời (trên bài của chính mình),
-- không còn chỉ Member bình luận. Thêm parent_id để trả lời 1 cấp (null = bình luận gốc).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'post_comments' AND column_name = 'member_id') THEN
        ALTER TABLE post_comments RENAME COLUMN member_id TO author_id;
    END IF;
END $$;

ALTER TABLE post_comments ADD COLUMN IF NOT EXISTS parent_id uuid REFERENCES post_comments(id);
