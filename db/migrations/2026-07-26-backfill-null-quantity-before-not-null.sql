-- WINE-28 / ADR 0016: quantity ("antal flaskor") blir obligatorisk data,
-- i samma bemärkelse som name redan är det - se
-- docs/adr/0016-quantity-also-mandatory.md.
--
-- Precis som WINE-17s owner_id-tilldelning (2026-07-25-assign-existing-
-- wines-to-testus.sql) måste den här backfyllningen köras FÖRE
-- schema.sql:s kommande "ALTER TABLE wines ALTER COLUMN quantity SET NOT
-- NULL" - annars kraschar den satsen vid nästa appstart mot kvarvarande
-- NULL-rader (samma fälla som owner_id/search_vector-sagorna, se
-- CLAUDE.md).
--
-- Backfyllvärdet är 1, inte 0 - en rad utan känt antal representerar ett
-- vin som faktiskt finns i samlingen (annars hade det inte importerats/
-- sparats över huvud taget), så 1 är den minst felaktiga gissningen,
-- samma standardvärde som webbformuläret nu förifyller nya vin med.
--
-- Körs EN gång, manuellt, mot produktionsdatabasen - se
-- db/migrations/2026-07-17-image-oid-to-bytea.sql för samma mönster.

BEGIN;

UPDATE wines SET quantity = 1 WHERE quantity IS NULL;

COMMIT;
