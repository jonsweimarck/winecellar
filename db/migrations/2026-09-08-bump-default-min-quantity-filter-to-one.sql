-- WINE-42: "Antal flaskor fler än" bytte semantik till "Antal flaskor
-- minst" (>= i stället för >, se docs/devlog.md och CLAUDE.md) - en
-- negativ tröskel var ett obekvämt sätt att se utdruckna viner igen.
-- Samtidigt bytte defaultvärdet för nya konton från 0 till 1
-- (RegistrationService).
--
-- Befintliga konton har redan ett sparat default_min_quantity_filter,
-- satt av WINE-41s ursprungliga engångsmigrering
-- (db/migrations-mappen, kolumnen fanns inte innan) - INTE ett aktivt
-- val gjort av kontoinnehavaren. Under den gamla ">"-semantiken betydde
-- ett sparat 0 "dölj utdruckna viner" (0 flaskor > 0 är falskt); under
-- den nya ">="-semantiken betyder samma sparade 0 i stället "visa allt,
-- även utdruckna viner" (0 >= 0 är sant) - en tyst beteendeändring för
-- alla befintliga konton om värdet lämnas orört.
--
-- Den här satsen bumpar bara konton som fortfarande har KVAR det
-- ursprungligt migrerade värdet 0 till det nya defaultvärdet 1, så det
-- upplevda beteendet (dölj utdruckna viner som standard) blir
-- oförändrat för dem. Ett konto som redan explicit sparat ett annat
-- värde (inklusive ett medvetet 0, satt via Inställningar EFTER att
-- WINE-41 lanserades) rörs inte - den kan inte skiljas här från
-- WINE-41s ursprungliga backfill, men riskerar i praktiken bara att
-- gälla en handfull konton som hunnit ändra filtret under den korta
-- perioden mellan WINE-41 och den här migreringen.
--
-- Körs EN gång, manuellt, mot produktionsdatabasen - se
-- db/migrations/2026-07-17-image-oid-to-bytea.sql för samma mönster.

BEGIN;

UPDATE users SET default_min_quantity_filter = 1 WHERE default_min_quantity_filter = 0;

COMMIT;
