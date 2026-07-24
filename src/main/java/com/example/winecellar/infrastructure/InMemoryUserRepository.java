package com.example.winecellar.infrastructure;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Testdubblett för kommande Cucumber-/enhetstester (WINE-11 och framåt) -
 * inte Spring-hanterad, samma mönster som {@link InMemoryWineRepository}.
 */
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public User save(User user) {
        User toStore = user.id() != null
                ? user
                : new User(new UserId(nextId.getAndIncrement()), user.username(), user.hashedPassword(), user.createdAt());
        users.put(toStore.id().value(), toStore);
        return toStore;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(users.get(id.value()));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return users.values().stream()
                .filter(user -> user.username().equals(username))
                .findFirst();
    }
}
