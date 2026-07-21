package com.example.winecellar.infrastructure;

import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.WineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entiteten har vuxit till att spegla hela Wine (se domain/Wine.java för
 * varför) - konstrueras via no-arg-konstruktorn + paketprivata settrar
 * istället för en positionell konstruktor med 20+ likartat typade
 * String/BigDecimal-fält, som vore lätt att kasta om av misstag.
 */
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

    @Column(columnDefinition = "text")
    private String region;

    @Column(columnDefinition = "text")
    private String subregion;

    @Column(columnDefinition = "text")
    private String grapes;

    private Integer vintage;

    private LocalDate purchaseDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private Integer quantity;

    @Column(columnDefinition = "text")
    private String purchaseReason;

    @Column(columnDefinition = "text")
    private String tastingNotes;

    @Enumerated(EnumType.STRING)
    private Rating ownRating;

    private String systembolagetProductNumber;

    @Column(columnDefinition = "text")
    private String systembolagetDescription;

    @Column(columnDefinition = "text")
    private String munskankarnaReview;

    @Enumerated(EnumType.STRING)
    private Rating munskankarnaRating;

    @Column(precision = 2, scale = 1)
    private BigDecimal vivinoRating;

    @Column(columnDefinition = "text")
    private String otherReference;

    private String location;

    /**
     * @Lob byte[] mappar till Postgres oid (large object) med Hibernates
     * standardinställningar, inte bytea - se CLAUDE.md. JdbcTypeCode(VARBINARY)
     * tvingar fram en riktig bytea-kolumn istället. Kräver en engångsmigrering
     * för redan existerande data - ddl-auto: update kan inte ändra en
     * kolumns typ, bara lägga till nya kolumner/tabeller.
     */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    private byte[] image;

    private String imageMimeType;

    protected WineEntity() {
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    WineType getWineType() {
        return wineType;
    }

    void setWineType(WineType wineType) {
        this.wineType = wineType;
    }

    String getProducer() {
        return producer;
    }

    void setProducer(String producer) {
        this.producer = producer;
    }

    String getCountry() {
        return country;
    }

    void setCountry(String country) {
        this.country = country;
    }

    String getRegion() {
        return region;
    }

    void setRegion(String region) {
        this.region = region;
    }

    String getSubregion() {
        return subregion;
    }

    void setSubregion(String subregion) {
        this.subregion = subregion;
    }

    String getGrapes() {
        return grapes;
    }

    void setGrapes(String grapes) {
        this.grapes = grapes;
    }

    Integer getVintage() {
        return vintage;
    }

    void setVintage(Integer vintage) {
        this.vintage = vintage;
    }

    LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    BigDecimal getPrice() {
        return price;
    }

    void setPrice(BigDecimal price) {
        this.price = price;
    }

    Integer getQuantity() {
        return quantity;
    }

    void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    String getPurchaseReason() {
        return purchaseReason;
    }

    void setPurchaseReason(String purchaseReason) {
        this.purchaseReason = purchaseReason;
    }

    String getTastingNotes() {
        return tastingNotes;
    }

    void setTastingNotes(String tastingNotes) {
        this.tastingNotes = tastingNotes;
    }

    Rating getOwnRating() {
        return ownRating;
    }

    void setOwnRating(Rating ownRating) {
        this.ownRating = ownRating;
    }

    String getSystembolagetProductNumber() {
        return systembolagetProductNumber;
    }

    void setSystembolagetProductNumber(String systembolagetProductNumber) {
        this.systembolagetProductNumber = systembolagetProductNumber;
    }

    String getSystembolagetDescription() {
        return systembolagetDescription;
    }

    void setSystembolagetDescription(String systembolagetDescription) {
        this.systembolagetDescription = systembolagetDescription;
    }

    String getMunskankarnaReview() {
        return munskankarnaReview;
    }

    void setMunskankarnaReview(String munskankarnaReview) {
        this.munskankarnaReview = munskankarnaReview;
    }

    Rating getMunskankarnaRating() {
        return munskankarnaRating;
    }

    void setMunskankarnaRating(Rating munskankarnaRating) {
        this.munskankarnaRating = munskankarnaRating;
    }

    BigDecimal getVivinoRating() {
        return vivinoRating;
    }

    void setVivinoRating(BigDecimal vivinoRating) {
        this.vivinoRating = vivinoRating;
    }

    String getOtherReference() {
        return otherReference;
    }

    void setOtherReference(String otherReference) {
        this.otherReference = otherReference;
    }

    String getLocation() {
        return location;
    }

    void setLocation(String location) {
        this.location = location;
    }

    byte[] getImage() {
        return image;
    }

    void setImage(byte[] image) {
        this.image = image;
    }

    String getImageMimeType() {
        return imageMimeType;
    }

    void setImageMimeType(String imageMimeType) {
        this.imageMimeType = imageMimeType;
    }
}
