package com.example.winecellar.infrastructure;

import com.example.winecellar.application.UserTokenRepository;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Testdubblett, samma mönster som {@link InMemoryUserRepository}. */
public class InMemoryUserTokenRepository implements UserTokenRepository {

    private final Map<Long, UserToken> tokens = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public UserToken save(UserToken token) {
        UserToken toStore = token.id() != null ? token
                : new UserToken(nextId.getAndIncrement(), token.userId(), token.purpose(), token.tokenHash(),
                        token.expiresAt());
        tokens.put(toStore.id(), toStore);
        return toStore;
    }

    @Override
    public Optional<UserToken> findByHash(Purpose purpose, String tokenHash) {
        return tokens.values().stream()
                .filter(t -> t.purpose() == purpose && t.tokenHash().equals(tokenHash))
                .findFirst();
    }

    @Override
    public boolean deleteById(Long id) {
        return tokens.remove(id) != null;
    }

    @Override
    public void deleteByUserAndPurpose(UserId userId, Purpose purpose) {
        tokens.values().removeIf(t -> t.userId().equals(userId) && t.purpose() == purpose);
    }

    @Override
    public void deleteAllByUser(UserId userId) {
        tokens.values().removeIf(t -> t.userId().equals(userId));
    }

    public int count() {
        return tokens.size();
    }
}
