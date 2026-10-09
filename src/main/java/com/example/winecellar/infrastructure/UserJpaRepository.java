package com.example.winecellar.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    /** Skiftlägesokänsligt (WINE-59); äldsta kontot vinner i det osannolika fallet två äldre konton skiljer sig bara i versaler. */
    Optional<UserEntity> findFirstByUsernameIgnoreCaseOrderByIdAsc(String username);

    @Modifying
    @Query("update UserEntity u set u.lastLoginAt = :at where u.id = :id")
    int updateLastLogin(@Param("id") Long id, @Param("at") Instant at);
}
