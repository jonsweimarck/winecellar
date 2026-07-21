-- Körs automatiskt vid varje appstart (spring.sql.init.mode: always, se
-- application.yml) - till skillnad från de manuella engångsskripten i
-- db/migrations/ (t.ex. 2026-07-17-image-oid-to-bytea.sql). Skillnaden:
-- den här migreringen är ren schema-DDL utan datamigrering (ingen
-- befintlig data ska flyttas/konverteras - Postgres beräknar kolumnens
-- värde automatiskt för både befintliga och nya rader), så den kan
-- köras om varje gång utan risk. ddl-auto: update kan bara lägga till
-- vanliga kolumner/tabeller, inte en GENERATED ALWAYS AS-kolumn eller
-- ett index, därav detta kompletterande schema.sql. Se README:s
-- "Filtrering, sökning och sortering" för bakgrunden till
-- fritextsökningen.
--
-- DROP + återskapa varje gång, INTE bara "ADD COLUMN IF NOT EXISTS"
-- (som den första versionen av den här filen gjorde) - Postgres har
-- inget "ALTER COLUMN ... SET EXPRESSION" för genererade kolumner, så
-- IF NOT EXISTS hade gjort filen till ett engångsskript i praktiken:
-- när druvor (grapes) lades till i sökuttrycket 2026-07-22 hade en
-- redan existerande search_vector-kolumn i produktionsdatabasen bara
-- tyst behållit sin GAMLA definition (utan grapes) för alltid, eftersom
-- IF NOT EXISTS aldrig hade kört ALTER-satsen igen. Med drop+återskapa
-- är schema.sql istället den enda sanningskällan för kolumnens
-- FAKTISKA definition just nu - varje appstart konvergerar databasen
-- mot vad som faktiskt står här, oavsett vad som fanns innan. Kostnaden
-- (hela search_vector räknas om för alla rader, index byggs om) är
-- försumbar för en samlingsstorlek i den här klassen.
--
-- spring.jpa.defer-datasource-initialization: true säkerställer att det
-- här körs EFTER att Hibernate skapat wines-tabellen (annars kraschar
-- ALTER TABLE mot en tabell som ännu inte finns, t.ex. vid en helt ny
-- databas).

ALTER TABLE wines DROP COLUMN IF EXISTS search_vector;

ALTER TABLE wines ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('swedish', coalesce(name, '') || ' ' || coalesce(producer, '') || ' ' || coalesce(grapes, '')), 'A') ||
        setweight(to_tsvector('swedish', coalesce(tasting_notes, '') || ' ' || coalesce(systembolaget_description, '') || ' ' || coalesce(munskankarna_review, '')), 'B')
    ) STORED;

CREATE INDEX IF NOT EXISTS wines_search_vector_idx ON wines USING GIN (search_vector);
