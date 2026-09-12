package com.example.winecellar.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ConversationJpaRepository extends JpaRepository<ConversationEntity, Long> {

    List<ConversationEntity> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<ConversationEntity> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);
}
