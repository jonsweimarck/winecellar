package com.example.winecellar.infrastructure;

import com.example.winecellar.application.UserTokenRepository;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class JpaUserTokenRepository implements UserTokenRepository {

    private final UserTokenJpaRepository jpaRepository;

    public JpaUserTokenRepository(UserTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public UserToken save(UserToken token) {
        UserTokenEntity entity = new UserTokenEntity();
        entity.setId(token.id());
        entity.setUserId(token.userId().value());
        entity.setPurpose(token.purpose().name());
        entity.setTokenHash(token.tokenHash());
        entity.setExpiresAt(token.expiresAt());
        entity.setPendingPasswordHash(token.pendingPasswordHash());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<UserToken> findByHash(Purpose purpose, String tokenHash) {
        return jpaRepository.findByPurposeAndTokenHash(purpose.name(), tokenHash).map(JpaUserTokenRepository::toDomain);
    }

    @Override
    public java.util.List<UserToken> findByUserAndPurpose(UserId userId, Purpose purpose) {
        return jpaRepository.findByUserIdAndPurposeOrderByIdAsc(userId.value(), purpose.name()).stream()
                .map(JpaUserTokenRepository::toDomain).toList();
    }

    /** Villkorad radering (DELETE ... WHERE id = ?) - antalet påverkade rader avgör vem som "vann". */
    @Override
    @Transactional
    public boolean deleteById(Long id) {
        return jpaRepository.deleteByTokenId(id) > 0;
    }

    @Override
    @Transactional
    public void deleteByUserAndPurpose(UserId userId, Purpose purpose) {
        jpaRepository.deleteByUserAndPurpose(userId.value(), purpose.name());
    }

    @Override
    @Transactional
    public void deleteAllByUser(UserId userId) {
        jpaRepository.deleteAllByUser(userId.value());
    }

    private static UserToken toDomain(UserTokenEntity entity) {
        return new UserToken(entity.getId(), new UserId(entity.getUserId()),
                Purpose.valueOf(entity.getPurpose()), entity.getTokenHash(), entity.getExpiresAt(),
                entity.getPendingPasswordHash());
    }
}
