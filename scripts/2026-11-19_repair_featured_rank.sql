BEGIN;
ALTER TABLE products ADD COLUMN IF NOT EXISTS featured_rank integer;
-- Repair already-seeded active badges. Do not reset the database or renew expired badges.
UPDATE products p
SET featured_rank = CASE s.tier WHEN 'DIAMOND' THEN 3 WHEN 'GOLD' THEN 2 WHEN 'SILVER' THEN 1 END
FROM member_subscriptions s
WHERE p.member_id = s.member_id
  AND p.featured_until > CURRENT_TIMESTAMP
  AND p.featured_rank IS NULL
  AND s.status = 'ACTIVE'
  AND s.featured_listing = true
  AND s.expires_at > CURRENT_TIMESTAMP
  AND s.tier IN ('DIAMOND', 'GOLD', 'SILVER');
COMMIT;
