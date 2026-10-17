package com.example.winecellar.infrastructure;

import java.util.List;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public JpaUserRepository(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        return toDomain(jpaRepository.save(toEntity(user)));
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.value()).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findFirstByUsernameIgnoreCaseOrderByIdAsc(username).map(JpaUserRepository::toDomain);
    }

    @Override
    public List<User> findAll() {
        return jpaRepository.findAll().stream().map(JpaUserRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void updateLastLogin(UserId id, Instant at) {
        jpaRepository.updateLastLogin(id.value(), at);
    }

    @Override
    @Transactional
    public boolean updateHashedPassword(UserId id, String hashedPassword) {
        return jpaRepository.updateHashedPassword(id.value(), hashedPassword) > 0;
    }

    @Override
    @Transactional
    public boolean activateAccount(UserId id, String hashedPassword) {
        return jpaRepository.activate(id.value(), hashedPassword) > 0;
    }

    @Override
    @Transactional
    public boolean deleteIfUnverified(UserId id) {
        return jpaRepository.deleteIfUnverified(id.value()) > 0;
    }

    @Override
    public List<User> findUnverifiedCreatedBefore(Instant createdBefore) {
        return jpaRepository.findByEmailVerifiedFalseAndCreatedAtBefore(createdBefore).stream()
                .map(JpaUserRepository::toDomain).toList();
    }

    @Override
    public void deleteById(UserId id) {
        jpaRepository.deleteById(id.value());
    }

    /** Används av acceptanstesterna för att nollställa tillstånd mellan scenarier. */
    public void deleteAll() {
        jpaRepository.deleteAll();
    }

    private static UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.id() != null ? user.id().value() : null);
        entity.setUsername(user.username());
        entity.setHashedPassword(user.hashedPassword());
        entity.setCreatedAt(user.createdAt());
        entity.setDefaultMinQuantityFilter(user.defaultMinQuantityFilter());
        entity.setOwnRatingFromScale(user.ownRatingFromScale());
        entity.setAdmin(user.admin());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setEmailVerified(user.emailVerified());
        return entity;
    }

    private static User toDomain(UserEntity entity) {
        return new User(
                new UserId(entity.getId()),
                entity.getUsername(),
                entity.getHashedPassword(),
                entity.getCreatedAt(),
                entity.getDefaultMinQuantityFilter() != null ? entity.getDefaultMinQuantityFilter() : 0,
                Boolean.TRUE.equals(entity.getOwnRatingFromScale()),
                Boolean.TRUE.equals(entity.getAdmin()),
                entity.getLastLoginAt(),
                // NULL kan bara förekomma före schema.sql hunnit backfilla - befintliga konton räknas som verifierade.
                !Boolean.FALSE.equals(entity.getEmailVerified()));
    }
}
