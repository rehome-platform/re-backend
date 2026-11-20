-- Run before deploying chat word masking. Existing messages remain unchanged.
ALTER TABLE IF EXISTS messages ADD COLUMN IF NOT EXISTS original_content text;
