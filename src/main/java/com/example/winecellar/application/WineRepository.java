package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;

import java.util.List;
import java.util.Optional;

public interface WineRepository {

    Wine save(Wine wine);

    List<Wine> findAll();

    Optional<Wine> findById(WineId id);

    void deleteById(WineId id);
}
