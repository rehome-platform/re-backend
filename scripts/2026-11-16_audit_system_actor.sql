-- System audit events have no user account.
ALTER TABLE public.audit_logs ALTER COLUMN actor_account_id DROP NOT NULL;
