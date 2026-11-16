BEGIN;

ALTER TABLE public.community_posts DROP CONSTRAINT IF EXISTS community_posts_status_check;

ALTER TABLE public.community_posts
    ADD COLUMN IF NOT EXISTS review_status varchar(255),
    ADD COLUMN IF NOT EXISTS review_reason text,
    ADD COLUMN IF NOT EXISTS reviewed_by uuid,
    ADD COLUMN IF NOT EXISTS reviewed_at timestamptz,
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.post_comments
    ADD COLUMN IF NOT EXISTS review_status varchar(255),
    ADD COLUMN IF NOT EXISTS review_reason text,
    ADD COLUMN IF NOT EXISTS reviewed_by uuid,
    ADD COLUMN IF NOT EXISTS reviewed_at timestamptz,
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

COMMIT;
