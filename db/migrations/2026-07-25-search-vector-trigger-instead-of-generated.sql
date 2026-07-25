-- Definitiv lösning på den upprepade "cannot alter type of a column
-- used by a generated column"-kraschen (tre produktionsdeployer i rad,
-- se CLAUDE.md). De tidigare migreringarna (droppa search_vector,
-- bredda kolumnerna direkt) höll bara till nästa gång Hibernates
-- ddl-auto: update av någon anledning fick skäl att röra wines-tabellen
-- igen - grundorsaken till VARFÖR Hibernate ibland vill röra
-- grapes/tasting_notes/systembolaget_description/munskankarna_review
-- är fortfarande inte helt klarlagd, men den går inte att lita på att
-- den slutar hända.
--
-- Den här migreringen tar bort själva MÖJLIGHETEN att krascha: en
-- vanlig tsvector-kolumn som en trigger skriver till (istället för
-- GENERATED ALWAYS AS) har ingen Postgres-begränsning mot att ALTER:a
-- kolumner den beror på - grapes m.fl. kan ändras fritt oavsett vad
-- Hibernate bestämmer sig för, framöver. Se schema.sql (samma
-- definition, körs automatiskt vid varje appstart efteråt) för den
-- fullständiga motiveringen.
--
-- Körs EN gång, manuellt, mot produktionsdatabasen, FÖRE nästa deploy -
-- måste köras innan Hibernates ddl-auto: update annars riskerar att
-- krascha på nytt innan schema.sql ens hinner köras (samma
-- kapplöpning som de två tidigare migreringarna).

BEGIN;

DROP TRIGGER IF EXISTS wines_search_vector_trigger ON wines;
ALTER TABLE wines DROP COLUMN IF EXISTS search_vector;

CREATE EXTENSION IF NOT EXISTS unaccent;

DROP TEXT SEARCH CONFIGURATION IF EXISTS swedish_unaccent CASCADE;
CREATE TEXT SEARCH CONFIGURATION swedish_unaccent (COPY = swedish);
ALTER TEXT SEARCH CONFIGURATION swedish_unaccent
    ALTER MAPPING FOR hword, hword_part, word
    WITH unaccent, swedish_stem;

ALTER TABLE wines ADD COLUMN search_vector tsvector;

CREATE OR REPLACE FUNCTION wines_update_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('swedish_unaccent', coalesce(NEW.name, '') || ' ' || coalesce(NEW.producer, '') || ' ' || coalesce(NEW.grapes, '')), 'A') ||
        setweight(to_tsvector('swedish_unaccent', coalesce(NEW.tasting_notes, '') || ' ' || coalesce(NEW.systembolaget_description, '') || ' ' || coalesce(NEW.munskankarna_review, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER wines_search_vector_trigger
    BEFORE INSERT OR UPDATE ON wines
    FOR EACH ROW EXECUTE FUNCTION wines_update_search_vector();

UPDATE wines SET search_vector =
    setweight(to_tsvector('swedish_unaccent', coalesce(name, '') || ' ' || coalesce(producer, '') || ' ' || coalesce(grapes, '')), 'A') ||
    setweight(to_tsvector('swedish_unaccent', coalesce(tasting_notes, '') || ' ' || coalesce(systembolaget_description, '') || ' ' || coalesce(munskankarna_review, '')), 'B');

CREATE INDEX IF NOT EXISTS wines_search_vector_idx ON wines USING GIN (search_vector);

COMMIT;
