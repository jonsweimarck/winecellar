package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;

import java.util.Optional;

public interface UserTokenRepository {

    UserToken save(UserToken token);

    Optional<UserToken> findByHash(Purpose purpose, String tokenHash);

    /**
     * Atomär: raderar tokenet och returnerar true bara för den ENDA anropare
     * vars radering faktiskt tog bort raden. Används som själva
     * engångsgrinden - två samtidiga inlösen kan aldrig båda lyckas.
     */
    boolean deleteById(Long id);

    /** Tar bort användarens alla tokens av ett visst slag (ny begäran ogiltigförklarar tidigare). */
    void deleteByUserAndPurpose(UserId userId, Purpose purpose);

    /** WINE-65: raderar alla tokens som gått ut vid tidpunkten (städning). @return antal raderade */
    int deleteExpired(java.time.Instant now);

    /** WINE-65: har användaren minst ett token kvar (efter att utgångna raderats = ett giltigt)? */
    boolean existsByUser(UserId userId);

    /** Används när ett konto raderas. */
    void deleteAllByUser(UserId userId);
}
