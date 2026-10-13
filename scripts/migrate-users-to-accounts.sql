-- PostgreSQL migration from the imported JWT demo schema.
-- Stop the application and back up the database before running this once.
-- Run before starting the Account version; accounts must not exist yet.
-- The users table is retained as a legacy copy. No application executes this file.
BEGIN;

LOCK TABLE users, refresh_tokens, email_otps IN ACCESS EXCLUSIVE MODE;

CREATE TEMP TABLE account_id_mapping ON COMMIT DROP AS
SELECT id AS legacy_id, gen_random_uuid() AS account_id FROM users;

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    google_id VARCHAR(255) UNIQUE,
    phone VARCHAR(255) UNIQUE,
    role VARCHAR(255) NOT NULL CHECK (role IN ('MEMBER', 'ORGANIZATION', 'MODERATOR', 'ADMIN')),
    status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BANNED')),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO accounts (id, email, password_hash, role, status, email_verified, created_at, updated_at)
SELECT m.account_id, u.email, u.password,
       CASE u.role WHEN 'ROLE_USER' THEN 'MEMBER' WHEN 'ROLE_ADMIN' THEN 'ADMIN' ELSE u.role END,
       CASE WHEN u.enabled THEN 'ACTIVE' ELSE 'BANNED' END,
       u.email_verified, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u JOIN account_id_mapping m ON m.legacy_id = u.id;

ALTER TABLE refresh_tokens ADD COLUMN account_id UUID;
UPDATE refresh_tokens r SET account_id = m.account_id
FROM account_id_mapping m WHERE r.user_id = m.legacy_id;
ALTER TABLE refresh_tokens ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE refresh_tokens ADD CONSTRAINT fk_refresh_token_account
    FOREIGN KEY (account_id) REFERENCES accounts(id);
-- Removing this column also removes its old FK to users.
ALTER TABLE refresh_tokens DROP COLUMN user_id;

UPDATE email_otps o SET id = m.account_id::text || ':' || split_part(o.id, ':', 2)
FROM account_id_mapping m WHERE split_part(o.id, ':', 1) = m.legacy_id::text;

COMMIT;
