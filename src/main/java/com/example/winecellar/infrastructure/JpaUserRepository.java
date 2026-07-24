package com.example.winecellar.infrastructure;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.springframework.stereotype.Repository;

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
        return jpaRepository.findByUsername(username).map(JpaUserRepository::toDomain);
    }

    private static UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.id() != null ? user.id().value() : null);
        entity.setUsername(user.username());
        entity.setHashedPassword(user.hashedPassword());
        entity.setCreatedAt(user.createdAt());
        return entity;
    }

    private static User toDomain(UserEntity entity) {
        return new User(
                new UserId(entity.getId()),
                entity.getUsername(),
                entity.getHashedPassword(),
                entity.getCreatedAt());
    }
}
