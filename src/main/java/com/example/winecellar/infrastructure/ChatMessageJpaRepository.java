package com.example.winecellar.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    long countByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
