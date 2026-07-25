-- WINE-17: knyter de ~30 vinerna som fanns i produktionsdatabasen innan
-- WINE-10 (owner_id-kolumnen) till det första riktiga, registrerade
-- kontot - "Testus" - istället för det gamla, nu snart borttagna
-- admin-kontot (WINE-15). Rör bara rader som ännu saknar ägare
-- (owner_id IS NULL) - ofarligt att köra om, gör ingenting om alla
-- viner redan har en ägare.
--
-- RAISE EXCEPTION om användarnamnet inte hittas, istället för att tyst
-- lämna owner_id oförändrat (NULL = NULL är annars ett stillatigande
-- no-op i UPDATE ... SET owner_id = (subquery som ger NULL)) - en
-- felstavning ska synas direkt, inte upptäckas efteråt som "varför ser
-- jag inga viner".
--
-- Körs EN gång, manuellt, mot produktionsdatabasen - se
-- db/migrations/2026-07-17-image-oid-to-bytea.sql för samma mönster.

DO $$
DECLARE
    target_user_id bigint;
BEGIN
    SELECT id INTO target_user_id FROM users WHERE username = 'Testus';

    IF target_user_id IS NULL THEN
        RAISE EXCEPTION 'Ingen användare med användarnamnet Testus hittades i users-tabellen';
    END IF;

    UPDATE wines SET owner_id = target_user_id WHERE owner_id IS NULL;
END $$;
