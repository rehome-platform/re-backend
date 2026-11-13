BEGIN;

ALTER TABLE item_withdrawal_requests
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE item_withdrawal_requests
    DROP CONSTRAINT IF EXISTS item_withdrawal_requests_status_check;

ALTER TABLE item_withdrawal_requests
    ADD CONSTRAINT item_withdrawal_requests_status_check
    CHECK (status IN ('PENDING_APPROVAL', 'PENDING_PICKUP', 'REJECTED', 'COMPLETED',
                      'EXPIRED', 'CANCELLED', 'CONVERTED_TO_DONATION'));

COMMIT;
