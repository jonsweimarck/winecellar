-- Körs automatiskt vid varje appstart (spring.sql.init.mode: always, se
-- application.yml) - till skillnad från de manuella engångsskripten i
-- db/migrations/ (t.ex. 2026-07-17-image-oid-to-bytea.sql). Skillnaden:
-- den här migreringen är ren schema-DDL utan datamigrering (ingen
-- befintlig data ska flyttas/konverteras - Postgres beräknar kolumnens
-- värde automatiskt för både befintliga och nya rader), så den kan vara
-- helt idempotent (IF NOT EXISTS överallt) och köras om varje gång utan
-- risk. ddl-auto: update kan bara lägga till vanliga kolumner/tabeller,
-- inte en GENERATED ALWAYS AS-kolumn eller ett index, därav detta
-- kompletterande schema.sql. Se README:s "Filtrering, sökning och
-- sortering" för bakgrunden till fritextsökningen.
--
-- spring.jpa.defer-datasource-initialization: true säkerställer att det
-- här körs EFTER att Hibernate skapat wines-tabellen (annars kraschar
-- ALTER TABLE mot en tabell som ännu inte finns, t.ex. vid en helt ny
-- databas).

ALTER TABLE wines ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('swedish', coalesce(name, '') || ' ' || coalesce(producer, '')), 'A') ||
        setweight(to_tsvector('swedish', coalesce(tasting_notes, '') || ' ' || coalesce(systembolaget_description, '') || ' ' || coalesce(munskankarna_review, '')), 'B')
    ) STORED;

CREATE INDEX IF NOT EXISTS wines_search_vector_idx ON wines USING GIN (search_vector);
