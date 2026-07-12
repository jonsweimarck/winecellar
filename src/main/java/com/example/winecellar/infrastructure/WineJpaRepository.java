package com.example.winecellar.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface WineJpaRepository extends JpaRepository<WineEntity, Long> {
}
