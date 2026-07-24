-- Uppföljning till 2026-07-25-drop-search-vector-before-column-widen.sql.
-- Den migreringen droppade bara search_vector och litade på att Hibernates
-- ddl-auto: update skulle bredda grapes/tasting_notes/
-- systembolaget_description/munskankarna_review till text vid nästa
-- uppstart - det visade sig otillförlitligt: Hibernates schemamigrering
-- kör flera ALTER-satser i samma transaktion i en ordning som inte är helt
-- förutsägbar (HashMap-baserad iteration i
-- SchemaManagementToolCoordinator), så en blockerad ALTER (om den råkar
-- köras medan search_vector ännu inte hunnit återskapas, eller om
-- breddningen av en annan kolumn misslyckats tidigare i samma transaktion)
-- kan antingen tystas bort (appen startar ändå, breddningen uteblir) eller
-- få HELA starten att krascha, beroende på ordning - se CLAUDE.md.
--
-- Den här migreringen gör breddningen SJÄLV, direkt i SQL, och tar bort
-- Hibernate från den kritiska vägen helt för de här fyra kolumnerna.
-- ALTER COLUMN ... TYPE text är ett ofarligt no-op om kolumnen redan är
-- text (går att köra om utan risk). Efter den här körningen har
-- Hibernates ddl-auto: update inget kvar att göra för de här kolumnerna -
-- dess förväntade typ (columnDefinition = "text" i WineEntity) matchar
-- redan verkligheten, så inget ALTER-försök (lyckat eller ej) kommer
-- behövas vid framtida uppstarter.
--
-- Körs EN gång, manuellt, mot produktionsdatabasen - se
-- db/migrations/2026-07-17-image-oid-to-bytea.sql för samma mönster.

BEGIN;

ALTER TABLE wines DROP COLUMN IF EXISTS search_vector;

ALTER TABLE wines ALTER COLUMN grapes TYPE text;
ALTER TABLE wines ALTER COLUMN tasting_notes TYPE text;
ALTER TABLE wines ALTER COLUMN systembolaget_description TYPE text;
ALTER TABLE wines ALTER COLUMN munskankarna_review TYPE text;

COMMIT;
