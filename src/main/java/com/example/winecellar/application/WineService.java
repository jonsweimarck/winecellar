package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WineService {

    private final WineRepository wineRepository;

    public WineService(WineRepository wineRepository) {
        this.wineRepository = wineRepository;
    }

    public Wine save(Wine wine) {
        return wineRepository.save(wine);
    }

    public List<Wine> listWines() {
        return wineRepository.findAll();
    }

    public Optional<Wine> findById(WineId id) {
        return wineRepository.findById(id);
    }

    public void removeWine(WineId id) {
        wineRepository.deleteById(id);
    }
}
