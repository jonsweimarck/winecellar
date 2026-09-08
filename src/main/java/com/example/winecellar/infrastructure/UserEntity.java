package com.example.winecellar.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String hashedPassword;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * WINE-41: sparat "Antal flaskor minst"-standardval för vinlistan
     * (semantiken ändrad från "fler än" i WINE-42). Medvetet UTAN
     * `nullable = false` här - kolumnen skärps till NOT NULL (med
     * DEFAULT 1 och en engångsbackfill av redan existerande användare)
     * i schema.sql i stället, samma mönster som
     * `wines.owner_id`/`wines.quantity` (se CLAUDE.md): Hibernates
     * `ddl-auto: update` kan lägga till en ny NULLABLE kolumn utan
     * problem, men skulle krascha mot redan existerande produktions-
     * användare om den själv försökte lägga till kolumnen som NOT NULL
     * (ingen DEFAULT-klausul härleds bara av annoteringen).
     */
    @Column(name = "default_min_quantity_filter")
    private Integer defaultMinQuantityFilter;

    protected UserEntity() {
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }

    String getHashedPassword() {
        return hashedPassword;
    }

    void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Integer getDefaultMinQuantityFilter() {
        return defaultMinQuantityFilter;
    }

    void setDefaultMinQuantityFilter(Integer defaultMinQuantityFilter) {
        this.defaultMinQuantityFilter = defaultMinQuantityFilter;
    }
}
