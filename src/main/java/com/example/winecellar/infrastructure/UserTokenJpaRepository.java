package com.example.winecellar.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface UserTokenJpaRepository extends JpaRepository<UserTokenEntity, Long> {

    Optional<UserTokenEntity> findByPurposeAndTokenHash(String purpose, String tokenHash);

    @Modifying
    @Query("delete from UserTokenEntity t where t.id = :id")
    int deleteByTokenId(@Param("id") Long id);

    @Modifying
    @Query("delete from UserTokenEntity t where t.userId = :userId and t.purpose = :purpose")
    int deleteByUserAndPurpose(@Param("userId") Long userId, @Param("purpose") String purpose);

    @Modifying
    @Query("delete from UserTokenEntity t where t.userId = :userId")
    int deleteAllByUser(@Param("userId") Long userId);
}
