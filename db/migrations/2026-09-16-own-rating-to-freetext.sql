-- WINE-50: "Eget betyg" (own_rating) blir fritext i stället för en sluten
-- betygsskala - munskankarna_rating (Munskänkarnas egen bedömning) är
-- HELT OFÖRÄNDRAT, fortsatt en Rating-enum + CHECK-constraint. Bara
-- own_rating berörs av den här migreringen.
--
-- own_rating ingår inte i search_vector-uttrycket (se schema.sql) - det
-- verifierades explicit innan den här migreringen skrevs, så den kända
-- "cannot alter type of a column used by a generated column"-fällan
-- (CLAUDE.md) kan inte uppstå här.
--
-- Släpper FÖRST CHECK-constrainten (namnet är Hibernates egen genererade
-- konvention för @Enumerated(EnumType.STRING), bekräftat mot en riktig
-- lokal databas innan den här migreringen skrevs) och breddar kolumnen
-- från varchar(255) till text (samma mönster som
-- 2026-07-25-widen-text-columns-directly.sql - fri text ska inte vara
-- begränsad till 255 tecken, även om ingen av de 29 etiketterna är i
-- närheten av så lång - längst är 40 tecken, kontrollerat explicit).
-- Konverterar DÄREFTER redan lagrade korta konstantnamn (t.ex. "R16")
-- till sina fulla svenska etiketter (Rating.R16.label() m.fl.) - annars
-- hade befintliga viner plötsligt visat en rå enum-konstant i UI:t efter
-- att CHECK-constrainten och Java-typen ändrats.
--
-- ORDNINGEN ÄR KRITISK - en verklig produktionskrasch hittades här (se
-- CLAUDE.md): en vanlig Postgres CHECK-constraint valideras per statement,
-- inte vid COMMIT, så BEGIN/COMMIT runt hela skriptet räddar INTE en
-- UPDATE som skriver en full etikett till en kolumn som fortfarande har
-- den gamla, bara-29-korta-koder-tillåtande CHECK-constrainten kvar -
-- satsen kraschar direkt med en constraint-överträdelse. Det missades
-- ursprungligen eftersom `mvn verify`s Testcontainers-databaser alltid är
-- färska och tomma (UPDATE-satsen blir då ett ofarligt no-op) - bara en
-- databas med FAKTISKA gamla data avslöjar felet.
--
-- Samma sats är även tillagd i schema.sql (körs automatiskt vid varje
-- appstart, självläkande/idempotent) - den här filen appliceras EN gång,
-- manuellt, mot produktionsdatabasen FÖRE deployen, samma mönster som
-- db/migrations/2026-07-17-image-oid-to-bytea.sql: lita inte på att en
-- enskild lyckad deploy bevisar att en ddl-auto: update-driven ALTER
-- (eller ens ett schema.sql-skript som råkar köras i fel ordning mot en
-- redan låst produktionskolumn) faktiskt går igenom problemfritt första
-- gången, se CLAUDE.md.

BEGIN;

ALTER TABLE wines DROP CONSTRAINT IF EXISTS wines_own_rating_check;
ALTER TABLE wines ALTER COLUMN own_rating TYPE text;

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
WHERE own_rating IS NOT NULL;

COMMIT;
