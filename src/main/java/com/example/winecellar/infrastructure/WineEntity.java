package com.example.winecellar.infrastructure;

import com.example.winecellar.domain.WineType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "wines")
public class WineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private WineType wineType;

    private String producer;

    private String country;

    private int vintage;

    private int quantity;

    private String location;

    @Lob
    private byte[] image;

    private String imageMimeType;

    protected WineEntity() {
    }

    WineEntity(Long id, String name, WineType wineType, String producer, String country, int vintage, int quantity,
               String location, byte[] image, String imageMimeType) {
        this.id = id;
        this.name = name;
        this.wineType = wineType;
        this.producer = producer;
        this.country = country;
        this.vintage = vintage;
        this.quantity = quantity;
        this.location = location;
        this.image = image;
        this.imageMimeType = imageMimeType;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    WineType getWineType() {
        return wineType;
    }

    String getProducer() {
        return producer;
    }

    String getCountry() {
        return country;
    }

    int getVintage() {
        return vintage;
    }

    int getQuantity() {
        return quantity;
    }

    String getLocation() {
        return location;
    }

    byte[] getImage() {
        return image;
    }

    String getImageMimeType() {
        return imageMimeType;
    }
}
