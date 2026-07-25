-- Körs automatiskt vid varje appstart (spring.sql.init.mode: always, se
-- application.yml) - till skillnad från de manuella engångsskripten i
-- db/migrations/ (t.ex. 2026-07-17-image-oid-to-bytea.sql). Se README:s
-- "Filtrering, sökning och sortering" för bakgrunden till
-- fritextsökningen.
--
-- Varje toppnivåsats avslutas med ";;" (inte bara ";") - se
-- application.ymls spring.sql.init.separator-kommentar för varför.
-- Semikolonen INUTI PL/pgSQL-funktionskroppen (search_vector-triggerns
-- "return new;") är vanliga ";" och rörs inte av det.
--
-- search_vector underhålls via en TRIGGER, inte GENERATED ALWAYS AS
-- (ändrat 2026-07-25, se CLAUDE.md - tre produktionsdeployer i rad
-- kraschade med "cannot alter type of a column used by a generated
-- column" när Hibernates ddl-auto: update ville röra grapes/
-- tasting_notes/systembolaget_description/munskankarna_review, trots
-- upprepade manuella migreringar som breddade kolumnerna till text).
-- En GENERATED-kolumn gör Postgres OVILLKORLIGT vägra ALTER på varje
-- kolumn den refererar - det gick inte att göra tillräckligt robust mot
-- att Hibernate av någon anledning fortsatte vilja röra de kolumnerna.
-- En vanlig tsvector-kolumn som en trigger skriver till har INGEN sådan
-- begränsning - grapes m.fl. går att ALTER:a fritt oavsett vad som
-- händer med search_vector, så den här klassen av krasch kan inte
-- uppstå igen, oavsett grundorsaken till Hibernates beteende.
--
-- DROP + återskapa varje gång (funktion/trigger/kolumn), inte bara
-- "IF NOT EXISTS" - samma "schema.sql är den enda sanningskällan för
-- FAKTISK definition just nu"-princip som redan gällde för den gamla
-- GENERATED-kolumnen (se git-historiken för det ursprungliga resonemanget
-- kring druvor/grapes 2026-07-22). Kostnaden (hela search_vector räknas
-- om för alla rader, index byggs om) är försumbar för en samlingsstorlek
-- i den här klassen.
--
-- spring.jpa.defer-datasource-initialization: true säkerställer att det
-- här körs EFTER att Hibernate skapat wines-tabellen (annars kraschar
-- ALTER TABLE mot en tabell som ännu inte finns, t.ex. vid en helt ny
-- databas).

DROP TRIGGER IF EXISTS wines_search_vector_trigger ON wines;;
ALTER TABLE wines DROP COLUMN IF EXISTS search_vector;;

-- WINE-7: sökning ska ignorera diakritiska tecken ("albarino" ska matcha
-- druvan "Albariño"). En vanlig unaccent(text)-funktion krävde tidigare
-- att kedjas in i en egen textsökkonfiguration eftersom GENERATED ALWAYS
-- AS krävde ett IMMUTABLE uttryck (unaccent() är bara STABLE) - det
-- kravet finns inte längre nu när search_vector inte är en genererad
-- kolumn, men samma textsökkonfiguration (swedish_unaccent) återanvänds
-- ändå, ingen anledning att bygga om något som redan fungerar.
CREATE EXTENSION IF NOT EXISTS unaccent;;

-- DROP/CREATE varje gång, inte CREATE ... IF NOT EXISTS - Postgres stöder
-- inte IF NOT EXISTS för CREATE TEXT SEARCH CONFIGURATION, och samma
-- "konvergera mot filens definition varje appstart"-princip som resten
-- av den här filen följer.
DROP TEXT SEARCH CONFIGURATION IF EXISTS swedish_unaccent CASCADE;;
CREATE TEXT SEARCH CONFIGURATION swedish_unaccent (COPY = swedish);;
ALTER TEXT SEARCH CONFIGURATION swedish_unaccent
    ALTER MAPPING FOR hword, hword_part, word
    WITH unaccent, swedish_stem;;

ALTER TABLE wines ADD COLUMN search_vector tsvector;;

-- CREATE OR REPLACE, inte DROP+CREATE - triggern nedan beror på
-- funktionen, och att droppa den hade krävt CASCADE (som också tar bort
-- triggern). OR REPLACE byter ut funktionskroppen i det befintliga
-- objektet, så triggern förblir opåverkad.
CREATE OR REPLACE FUNCTION wines_update_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('swedish_unaccent', coalesce(NEW.name, '') || ' ' || coalesce(NEW.producer, '') || ' ' || coalesce(NEW.grapes, '')), 'A') ||
        setweight(to_tsvector('swedish_unaccent', coalesce(NEW.tasting_notes, '') || ' ' || coalesce(NEW.systembolaget_description, '') || ' ' || coalesce(NEW.munskankarna_review, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;;

CREATE TRIGGER wines_search_vector_trigger
    BEFORE INSERT OR UPDATE ON wines
    FOR EACH ROW EXECUTE FUNCTION wines_update_search_vector();;

-- Fyller i search_vector för redan existerande rader - triggern ovan
-- täcker bara framtida INSERT/UPDATE, inte rader som redan låg i
-- tabellen innan kolumnen (åter)skapades av satsen ovan.
UPDATE wines SET search_vector =
    setweight(to_tsvector('swedish_unaccent', coalesce(name, '') || ' ' || coalesce(producer, '') || ' ' || coalesce(grapes, '')), 'A') ||
    setweight(to_tsvector('swedish_unaccent', coalesce(tasting_notes, '') || ' ' || coalesce(systembolaget_description, '') || ' ' || coalesce(munskankarna_review, '')), 'B');;

CREATE INDEX IF NOT EXISTS wines_search_vector_idx ON wines USING GIN (search_vector);;

-- Namn är sedan 2026-07-22 det enda obligatoriska fältet (se CLAUDE.md) -
-- vintage/quantity var tidigare Java-primitiver (int) och fick därför
-- automatiskt en NOT NULL-kolumn av Hibernate när tabellen skapades.
-- ddl-auto: update lägger bara till nya kolumner/tabeller, det lättar
-- aldrig på en befintlig NOT NULL-begränsning även om Java-typen ändras
-- till en nullable Integer - därav den här kompletterande satsen.
-- DROP NOT NULL är själv idempotent i Postgres (ingen "IF EXISTS" behövs
-- - att köra den mot en redan nullable kolumn är ett ofarligt no-op).
ALTER TABLE wines ALTER COLUMN vintage DROP NOT NULL;;
ALTER TABLE wines ALTER COLUMN quantity DROP NOT NULL;;
