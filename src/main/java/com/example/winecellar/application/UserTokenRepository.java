package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;

import java.util.Optional;

public interface UserTokenRepository {

    UserToken save(UserToken token);

    Optional<UserToken> findByHash(Purpose purpose, String tokenHash);

    void deleteById(Long id);

    /** Tar bort användarens alla tokens av ett visst slag (ny begäran ogiltigförklarar tidigare). */
    void deleteByUserAndPurpose(UserId userId, Purpose purpose);

    /** Används när ett konto raderas. */
    void deleteAllByUser(UserId userId);
}
