-- WINE-61: adminflagga på users. Idempotent (samma satser som körs vid varje
-- appstart via schema.sql). Ingen befintlig användare blir admin - alla rader
-- får false. Sätt första admin själv efteråt:
--
--   UPDATE users SET is_admin = true WHERE username = '<användarnamn>';

BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin boolean;
UPDATE users SET is_admin = false WHERE is_admin IS NULL;
ALTER TABLE users ALTER COLUMN is_admin SET DEFAULT false;
ALTER TABLE users ALTER COLUMN is_admin SET NOT NULL;

COMMIT;
