package com.example.winecellar.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Ny tabell (WINE-48) - till skillnad från {@code wines.owner_id} (se
 * CLAUDE.md, Kända fällor) finns ingen `ddl-auto: update`-ALTER-risk här,
 * eftersom tabellen skapas från grunden. `owner`-relationen är EAGER av
 * samma skäl som {@code WineEntity.owner}: `open-in-view: false` stänger
 * Hibernate-sessionen innan adaptern hinner läsa en lat proxy.
 */
@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationEntity() {
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    UserEntity getOwner() {
        return owner;
    }

    void setOwner(UserEntity owner) {
        this.owner = owner;
    }

    String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
