BEGIN;

CREATE TABLE IF NOT EXISTS donation_photos (
    donation_id uuid NOT NULL REFERENCES donations(id) ON DELETE CASCADE,
    display_order integer NOT NULL,
    url varchar(2048) NOT NULL,
    PRIMARY KEY (donation_id, display_order)
);

ALTER TABLE donations DROP CONSTRAINT IF EXISTS donations_status_check;

ALTER TABLE products ADD COLUMN IF NOT EXISTS featured_rank integer;

UPDATE products p
SET featured_rank = CASE s.tier WHEN 'DIAMOND' THEN 3 WHEN 'GOLD' THEN 2 WHEN 'SILVER' THEN 1 END
FROM member_subscriptions s
WHERE p.member_id = s.member_id
  AND p.featured_until > CURRENT_TIMESTAMP
  AND p.featured_rank IS NULL
  AND s.status = 'ACTIVE'
  AND s.expires_at > CURRENT_TIMESTAMP
  AND s.tier IN ('DIAMOND', 'GOLD', 'SILVER');

COMMIT;
