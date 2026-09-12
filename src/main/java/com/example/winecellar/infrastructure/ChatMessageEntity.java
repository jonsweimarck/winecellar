package com.example.winecellar.infrastructure;

import com.example.winecellar.domain.ChatMessage.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * {@code content} är fritext utan längdbegränsning - `columnDefinition =
 * "text"`, samma mönster som `WineEntity`s fritextfält (t.ex.
 * `tastingNotes`), inte `@Lob` (som för `byte[]` mappar till Postgres
 * `oid` istället för `bytea`, se CLAUDE.md, Kända fällor - odokumenterat
 * för `String`, men ingen anledning att chansa när ett redan beprövat
 * mönster finns i samma kodbas). `role` är en fast, sluten mängd precis
 * som {@code WineType}/{@code Rating} (`@Enumerated(EnumType.STRING)`, ger
 * en `CHECK`-constraint på samma sätt).
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessageEntity() {
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    ConversationEntity getConversation() {
        return conversation;
    }

    void setConversation(ConversationEntity conversation) {
        this.conversation = conversation;
    }

    Role getRole() {
        return role;
    }

    void setRole(Role role) {
        this.role = role;
    }

    String getContent() {
        return content;
    }

    void setContent(String content) {
        this.content = content;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
