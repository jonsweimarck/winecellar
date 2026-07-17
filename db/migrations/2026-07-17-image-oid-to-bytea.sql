-- Engångsmigrering: wines.image var i praktiken Postgres oid (large
-- object) p.g.a. Hibernates standardmappning av @Lob byte[], inte bytea
-- som avsett - se README.md ("Bilder i bytea, inte objektlagring") och
-- CLAUDE.md för bakgrunden. Konverterar till en riktig bytea-kolumn och
-- städar bort de gamla large objects som annars blir föräldralösa
-- (Postgres tar inte bort dem automatiskt när en rad tas bort eller
-- kolumnen droppas).
--
-- Körs EN gång, manuellt, mot en riktig databas - se README.md:s
-- "Bilder i bytea, inte objektlagring" för det fullständiga kommandot.
-- Inte tänkt att köras igen; kvarlämnad i repot för spårbarhet, inte som
-- en återanvändbar migreringsmekanism (projektet har varken Flyway eller
-- Liquibase, se CLAUDE.md/README.md om ddl-auto: update).

BEGIN;

ALTER TABLE wines ADD COLUMN image_bytea bytea;

UPDATE wines SET image_bytea = lo_get(image) WHERE image IS NOT NULL;

SELECT lo_unlink(image) FROM wines WHERE image IS NOT NULL;

ALTER TABLE wines DROP COLUMN image;
ALTER TABLE wines RENAME COLUMN image_bytea TO image;

COMMIT;
