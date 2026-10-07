-- WINE-57: Orange som ny vintyp. Hibernates ddl-auto: update uppdaterar
-- INTE en redan existerande CHECK-constraint på wine_type (den skapas bara
-- vid tabellskapande), så constrainten måste bytas explicit. Samma sats
-- finns i schema.sql (idempotent, körs vid varje appstart) - den här filen
-- kan köras manuellt mot produktionsdatabasen före deployen.
BEGIN;

ALTER TABLE wines DROP CONSTRAINT IF EXISTS wines_wine_type_check;
ALTER TABLE wines ADD CONSTRAINT wines_wine_type_check
    CHECK (wine_type IN ('RED', 'WHITE', 'ROSE', 'ORANGE', 'SPARKLING', 'FORTIFIED'));

COMMIT;
