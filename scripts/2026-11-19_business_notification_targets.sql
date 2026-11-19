BEGIN;
-- New targets: ORDER, RETURN_REQUEST, CONSIGNMENT_FORM, PAYOUT. No CHECK is recreated.
ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_target_type_check;
ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
COMMIT;
