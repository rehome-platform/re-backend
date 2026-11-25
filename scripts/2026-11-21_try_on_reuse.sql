-- Run on the intended PostgreSQL database before deploying the backend.
-- Existing generations remain valid; NULL fingerprints are not reused.
ALTER TABLE try_on_images ADD COLUMN IF NOT EXISTS generation_fingerprint varchar(64);
CREATE INDEX IF NOT EXISTS idx_try_on_reuse
    ON try_on_images (member_id, generation_fingerprint, created_at DESC);

CREATE TABLE IF NOT EXISTS try_on_shared_assets (
    fingerprint varchar(64) PRIMARY KEY,
    image_url varchar(2048) NOT NULL,
    created_at timestamptz NOT NULL
);
ALTER TABLE try_on_images ADD COLUMN IF NOT EXISTS shared_asset_fingerprint varchar(64)
    REFERENCES try_on_shared_assets(fingerprint);
ALTER TABLE try_on_images ADD COLUMN IF NOT EXISTS quota_charged boolean NOT NULL DEFAULT true;
ALTER TABLE try_on_images ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
CREATE INDEX IF NOT EXISTS idx_try_on_shared_reference ON try_on_images(shared_asset_fingerprint);

-- Adopt previously fingerprinted mannequin results. Never guess missing input fingerprints.
INSERT INTO try_on_shared_assets (fingerprint, image_url, created_at)
SELECT DISTINCT ON (generation_fingerprint) generation_fingerprint, image_url, created_at
FROM try_on_images
WHERE generation_fingerprint IS NOT NULL
ORDER BY generation_fingerprint, created_at DESC, id DESC
ON CONFLICT (fingerprint) DO NOTHING;

UPDATE try_on_images i SET shared_asset_fingerprint = a.fingerprint
FROM try_on_shared_assets a
WHERE i.generation_fingerprint = a.fingerprint AND i.image_url = a.image_url
  AND i.shared_asset_fingerprint IS NULL;
