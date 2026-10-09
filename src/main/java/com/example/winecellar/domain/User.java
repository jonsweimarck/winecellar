package com.example.winecellar.domain;

import java.time.Instant;

/**
 * Tunt, som {@link Wine} (se ADR 0001) - inga regler att skydda här.
 * Unikhetskontroll för `username` hör hemma i registreringsflödet
 * (WINE-11), inte i domänobjektet. `hashedPassword` är alltid redan
 * hashat när det når det här objektet - hashningen sker i
 * infrastruktur-/webblagret via den befintliga `PasswordEncoder`-beanen
 * (se `SecurityConfig`), inte här.
 *
 * `defaultMinQuantityFilter` (WINE-41, semantiken ändrad från "fler än"
 * till "minst" i WINE-42) är vinlistans förvalda "Antal flaskor minst"-
 * filter för den här användaren - vad `GET /` faller tillbaka till när
 * requesten inte har en explicit `minQuantity`-queryparameter (se
 * WineController). Default 1 för nya konton (se RegistrationService) -
 * "minst 1", dvs. utdruckna viner (antal 0) döljs som standard, utan att
 * kräva ett obekvämt negativt värde för att visa dem igen (sätt filtret
 * till 0 för det).
 *
 * `ownRatingFromScale` (WINE-50) styr hur vinformuläret låter användaren
 * fylla i "Eget betyg" - avmarkerad (default för nya konton, se
 * RegistrationService) ger ett fritextfält, ikryssad ger en dropdown med
 * munskänkarnas 29 fasta etiketter. Oavsett vilket sparas alltid bara den
 * rena textsträngen i `Wine.ownRating()` - inställningen styr bara
 * FORMULÄRET, inte vad som faktiskt lagras (se Wine.java).
 *
 * `admin` (WINE-61) ger tillgång till adminsidan (`/admin`). Ett nytt konto
 * är aldrig admin (se RegistrationService) - första admin sätts manuellt i
 * databasen, fler görs av en admin via adminsidan.
 *
 * `lastLoginAt` (WINE-62) är tidpunkten för senaste lyckade inloggning
 * (formulärinloggning eller remember-me-återinloggning). Sätts till
 * `createdAt` vid registrering och uppdateras sedan via en egen riktad
 * skrivning (`UserRepository.updateLastLogin`) som inte ska skrivas över av
 * normal läs-ändra-spara. Ett mycket snävt race (SettingsController/
 * AdminService.makeAdmin läser och sparar hela User) kan i teorin ge ett
 * något äldre värde - nästa inloggning rättar det. Varje kod som kopierar en
 * User måste bära vidare fältet.
 *
 * `emailVerified` (WINE-59, se ADR 0026): användarnamnet är en e-postadress
 * och kontot får inte logga in förrän adressen verifierats via en länk i ett
 * mail. Alla konton som fanns före WINE-59 markerades som verifierade av
 * migreringen. Använd {@code withX}-metoderna för att ändra ETT fält - de bär
 * vidare resten, så att en kopia aldrig tyst tappar t.ex. adminflaggan eller
 * verifieringen.
 */
public record User(
        UserId id,
        String username,
        String hashedPassword,
        Instant createdAt,
        int defaultMinQuantityFilter,
        boolean ownRatingFromScale,
        boolean admin,
        Instant lastLoginAt,
        boolean emailVerified
) {


    public User withHashedPassword(String newHash) {
        return new User(id, username, newHash, createdAt, defaultMinQuantityFilter, ownRatingFromScale, admin,
                lastLoginAt, emailVerified);
    }

    public User withEmailVerified(boolean verified) {
        return new User(id, username, hashedPassword, createdAt, defaultMinQuantityFilter, ownRatingFromScale,
                admin, lastLoginAt, verified);
    }

    public User withAdmin(boolean newAdmin) {
        return new User(id, username, hashedPassword, createdAt, defaultMinQuantityFilter, ownRatingFromScale,
                newAdmin, lastLoginAt, emailVerified);
    }

    public User withDefaultMinQuantityFilter(int value) {
        return new User(id, username, hashedPassword, createdAt, value, ownRatingFromScale, admin,
                lastLoginAt, emailVerified);
    }

    public User withOwnRatingFromScale(boolean value) {
        return new User(id, username, hashedPassword, createdAt, defaultMinQuantityFilter, value, admin,
                lastLoginAt, emailVerified);
    }

    public record UserId(Long value) {
    }
}
