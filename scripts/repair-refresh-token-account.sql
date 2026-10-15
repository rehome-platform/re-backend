-- For a database where accounts already exists, but refresh_tokens.user_id is bigint.
-- Stop the backend and back up the database before executing.
-- Retains user_id and existing token rows for compatibility with legacy data.
BEGIN;
LOCK TABLE refresh_tokens, users, accounts IN ACCESS EXCLUSIVE MODE;

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS account_id UUID;
UPDATE refresh_tokens r SET account_id = a.id
FROM users u JOIN accounts a ON a.email = u.email
WHERE r.user_id = u.id AND r.account_id IS NULL;

-- Fail and roll back if any legacy token cannot be linked to an existing account.
ALTER TABLE refresh_tokens ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN user_id DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'refresh_tokens'::regclass
          AND conname = 'fk_refresh_token_account'
    ) THEN
        ALTER TABLE refresh_tokens ADD CONSTRAINT fk_refresh_token_account
            FOREIGN KEY (account_id) REFERENCES accounts(id);
    END IF;
END $$;
COMMIT;
