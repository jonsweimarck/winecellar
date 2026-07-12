package com.example.winecellar.domain;

public record Wine(
        WineId id,
        String name,
        WineType wineType,
        String producer,
        String country,
        int vintage,
        int quantity,
        String location
) {

    public Wine withQuantity(int newQuantity) {
        return new Wine(id, name, wineType, producer, country, vintage, newQuantity, location);
    }

    public record WineId(Long value) {
    }
}
