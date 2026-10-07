-- WINE-62: tidsstämpel för senaste lyckade inloggning på users. Idempotent
-- (samma satser som körs vid varje appstart via schema.sql). Befintliga konton
-- får aktuell tid. Backfill sker INNAN NOT NULL sätts.

BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at timestamptz;
UPDATE users SET last_login_at = now() WHERE last_login_at IS NULL;
ALTER TABLE users ALTER COLUMN last_login_at SET DEFAULT now();
ALTER TABLE users ALTER COLUMN last_login_at SET NOT NULL;

COMMIT;
