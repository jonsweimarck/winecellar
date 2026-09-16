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
-- vintage var tidigare en Java-primitiv (int) och fick därför automatiskt
-- en NOT NULL-kolumn av Hibernate när tabellen skapades. ddl-auto: update
-- lägger bara till nya kolumner/tabeller, det lättar aldrig på en
-- befintlig NOT NULL-begränsning även om Java-typen ändras till en
-- nullable Integer - därav den här kompletterande satsen. DROP NOT NULL
-- är själv idempotent i Postgres (ingen "IF EXISTS" behövs - att köra den
-- mot en redan nullable kolumn är ett ofarligt no-op).
ALTER TABLE wines ALTER COLUMN vintage DROP NOT NULL;;

-- WINE-28/ADR 0016: quantity ("antal flaskor") blev obligatorisk igen
-- (se docs/adr/0016-quantity-also-mandatory.md) - motsatt riktning av
-- raden ovan, satt direkt i SQL av samma skäl som owner_id nedan
-- (Hibernates ddl-auto: update lättar aldrig på en begränsning den inte
-- känner till från annoteringarna, och kan inte heller pålitligt skärpa
-- en befintlig kolumn - se CLAUDE.md om search_vector-sagan). Idempotent
-- - SET NOT NULL på en redan NOT NULL-kolumn är ett ofarligt no-op.
-- Kräver att 2026-07-26-backfill-null-quantity-before-not-null.sql redan
-- körts (alla rader har ett antal) - annars skulle den här satsen
-- misslyckas mot kvarvarande NULL-rader.
ALTER TABLE wines ALTER COLUMN quantity SET NOT NULL;;

-- WINE-15: owner_id är obligatoriskt sedan admin-kontot (den enda
-- kodvägen som kunde skapa ett ägarlöst vin, se WineController/
-- SecurityConfig) togs bort - varje inloggad användare är numera ett
-- riktigt konto i users-tabellen. Satt direkt i SQL, INTE via
-- @JoinColumn(nullable = false) i WineEntity (Hibernates ddl-auto:
-- update har visat sig opålitligt för den här sortens ALTER hela dagen,
-- se CLAUDE.md om search_vector-sagan) - Hibernate lättar aldrig på en
-- begränsning den inte känner till från annoteringarna (samma princip
-- som vintage/quantity ovan, fast i motsatt riktning), så den här raden
-- är konfliktfri med ddl-auto: update. Idempotent - SET NOT NULL på en
-- redan NOT NULL-kolumn är ett ofarligt no-op. Kräver att WINE-17s
-- migrering redan körts (alla rader har en ägare) - annars skulle den
-- här satsen misslyckas mot kvarvarande NULL-rader.
ALTER TABLE wines ALTER COLUMN owner_id SET NOT NULL;;

-- WINE-41: vinlistans "Antal flaskor minst"-filter (semantiken ändrad
-- från "fler än" i WINE-42, se CLAUDE.md) kan sparas per användare
-- (Inställningar) och används som GET /:s default när requesten inte
-- har en explicit minQuantity-queryparameter (se
-- WineController/CurrentUser). Kolumnen läggs medvetet till som
-- NULLABLE i UserEntitys annotering och skärps till NOT NULL här i SQL
-- i stället - samma mönster som owner_id/quantity ovan (se CLAUDE.md):
-- Hibernates ddl-auto: update kan lägga till en helt ny NULLABLE
-- kolumn utan problem, men skulle krascha mot redan existerande
-- produktionsanvändare om den själv försökte lägga till kolumnen som
-- NOT NULL (ingen DEFAULT-klausul härleds bara av annoteringen, så
-- befintliga rader hade fått NULL och blockerat en efterföljande NOT
-- NULL-begränsning). Idempotent: ADD COLUMN IF NOT EXISTS, backfillen
-- av NULL-rader, och SET NOT NULL på en redan NOT NULL-kolumn är alla
-- ofarliga no-op vid upprepad körning. Backfillvärdet och DEFAULT-
-- klausulen är 1 (WINE-42s nya default för nya konton, se
-- RegistrationService) - gäller bara en kolumn som fortfarande är NULL,
-- dvs. en miljö som aldrig kört WINE-41s ursprungliga migrering. En
-- redan satt 0 (från WINE-41s ursprungliga backfill) rörs INTE här -
-- den migreringen är en egen, engångskörd sats, se
-- db/migrations/2026-09-08-bump-default-min-quantity-filter-to-one.sql.
ALTER TABLE users ADD COLUMN IF NOT EXISTS default_min_quantity_filter integer;;
UPDATE users SET default_min_quantity_filter = 1 WHERE default_min_quantity_filter IS NULL;;
ALTER TABLE users ALTER COLUMN default_min_quantity_filter SET DEFAULT 1;;
ALTER TABLE users ALTER COLUMN default_min_quantity_filter SET NOT NULL;;

-- WINE-50: "Eget betyg" (own_rating) blir fritext i stället för en sluten
-- betygsskala - munskankarna_rating är HELT OFÖRÄNDRAT (fortsatt
-- Rating-enum + CHECK). own_rating ingår inte i search_vector-uttrycket
-- ovan, så den här ändringen kan inte trigga sagan om ALTER mot en
-- kolumn en genererad kolumn beror på (se CLAUDE.md) - verifierat
-- explicit innan den här migreringen skrevs, inte antaget.
--
-- Konverterar först redan lagrade korta konstantnamn (t.ex. "R16") till
-- sina fulla svenska etiketter - annars hade befintliga viner plötsligt
-- visat en rå enum-konstant i UI:t efter att CHECK-constrainten och
-- Java-typen ändrats. CASE-satsen är självläkande/idempotent: en rad vars
-- own_rating redan är en full etikett (eller ett fritextvärde, eller
-- NULL) matchar ingen av grenarna och lämnas orörd av ELSE own_rating,
-- så satsen kan köras om vid varje appstart utan att skada redan
-- konverterad eller ny fritextdata - samma "konvergera mot filens
-- definition"-princip som resten av den här filen.
UPDATE wines SET own_rating = CASE own_rating
    WHEN 'R20' THEN '20 (18 - 20 Exceptionellt vin)'
    WHEN 'R19_5' THEN '19,5 (18 - 20 Exceptionellt vin)'
    WHEN 'R19' THEN '19 (18 - 20 Exceptionellt vin)'
    WHEN 'R18_5' THEN '18,5 (18 - 20 Exceptionellt vin)'
    WHEN 'R18' THEN '18 (18 - 20 Exceptionellt vin)'
    WHEN 'R17_5' THEN '17,5 (15 - 17,5 Högklassigt vin)'
    WHEN 'R17' THEN '17 (15 - 17,5 Högklassigt vin)'
    WHEN 'R16_5' THEN '16,5 (15 - 17,5 Högklassigt vin)'
    WHEN 'R16' THEN '16 (15 - 17,5 Högklassigt vin)'
    WHEN 'R15_5' THEN '15,5 (15 - 17,5 Högklassigt vin)'
    WHEN 'R15' THEN '15 (15 - 17,5 Högklassigt vin)'
    WHEN 'R14_5' THEN '14,5 (12 - 14,5 Bra till mycket bra vin)'
    WHEN 'R14' THEN '14 (12 - 14,5 Bra till mycket bra vin)'
    WHEN 'R13_5' THEN '13,5 (12 - 14,5 Bra till mycket bra vin)'
    WHEN 'R13' THEN '13 (12 - 14,5 Bra till mycket bra vin)'
    WHEN 'R12_5' THEN '12,5 (12 - 14,5 Bra till mycket bra vin)'
    WHEN 'R12' THEN '12 (12 - 14,5 Bra till mycket bra vin)'
    WHEN 'R11_5' THEN '11,5 (9 - 11,5 Medelbra vin)'
    WHEN 'R11' THEN '11 (9 - 11,5 Medelbra vin)'
    WHEN 'R10_5' THEN '10,5 (9 - 11,5 Medelbra vin)'
    WHEN 'R10' THEN '10 (9 - 11,5 Medelbra vin)'
    WHEN 'R9_5' THEN '9,5 (9 - 11,5 Medelbra vin)'
    WHEN 'R9' THEN '9 (9 - 11,5 Medelbra vin)'
    WHEN 'R8_5' THEN '8,5 (6 - 8,5 Enkel vin)'
    WHEN 'R8' THEN '8 (6 - 8,5 Enkel vin)'
    WHEN 'R7_5' THEN '7,5 (6 - 8,5 Enkel vin)'
    WHEN 'R7' THEN '7 (6 - 8,5 Enkel vin)'
    WHEN 'R6_5' THEN '6,5 (6 - 8,5 Enkel vin)'
    WHEN 'R6' THEN '6 (6 - 8,5 Enkel vin)'
    ELSE own_rating
END
WHERE own_rating IS NOT NULL;;

-- Constraintnamnet är Hibernates egen genererade konvention för
-- @Enumerated(EnumType.STRING) (verifierat mot en riktig lokal databas
-- innan den här migreringen skrevs, inte antaget) - DROP CONSTRAINT IF
-- EXISTS är ett ofarligt no-op vid upprepad körning eller mot en
-- databas som redan konverterats. Kolumnen breddas samtidigt från
-- varchar(255) (samma Hibernate-default som gav wine_type/
-- munskankarna_rating sin bredd) till text, samma mönster som
-- 2026-07-25-widen-text-columns-directly.sql - fri text ska inte vara
-- begränsad till 255 tecken. ALTER COLUMN ... TYPE text är ett ofarligt
-- no-op om kolumnen redan är text.
ALTER TABLE wines DROP CONSTRAINT IF EXISTS wines_own_rating_check;;
ALTER TABLE wines ALTER COLUMN own_rating TYPE text;;

-- Vinformulärets val mellan dropdown (munskänkarnas 29 etiketter) och
-- fritextfält för "Eget betyg" - sparat per användare, samma NULLABLE-i-
-- Java/NOT NULL-i-SQL-mönster som default_min_quantity_filter ovan.
-- Default false (fritext) för både nya konton (RegistrationService) och
-- redan existerande konton (som aldrig aktivt valt något).
ALTER TABLE users ADD COLUMN IF NOT EXISTS own_rating_from_scale boolean;;
UPDATE users SET own_rating_from_scale = false WHERE own_rating_from_scale IS NULL;;
ALTER TABLE users ALTER COLUMN own_rating_from_scale SET DEFAULT false;;
ALTER TABLE users ALTER COLUMN own_rating_from_scale SET NOT NULL;;
