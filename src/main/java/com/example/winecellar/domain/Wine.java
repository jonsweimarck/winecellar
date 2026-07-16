package com.example.winecellar.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * `image`s genererade equals()/hashCode() jämför referens, inte innehåll -
 * en Records-egenhet för array-komponenter. Två Wine-instanser med olikt
 * array-objekt men identiska bytes räknas alltså som olika. Jämför
 * {@code image()}-innehåll explicit (t.ex. Arrays.equals) om det behövs,
 * lita inte på Wine.equals() för det fältet.
 *
 * Fälten utöver de ursprungliga sju (name/wineType/producer/country/
 * vintage/quantity/location) speglar Vinlista.xlsx en-till-en (se
 * tools/import-excel/) och är ännu inte redigerbara i webb-UI:t - bara
 * satta via importskriptet. De flesta är nullable, konsekvent med att
 * webbformuläret bara sätter kärnfälten.
 */
public record Wine(
        WineId id,
        String name,
        WineType wineType,
        String producer,
        String country,
        String region,
        String subregion,
        String grapes,
        int vintage,
        LocalDate purchaseDate,
        BigDecimal price,
        int quantity,
        String purchaseReason,
        String tastingNotes,
        Rating ownRating,
        String systembolagetProductNumber,
        String systembolagetDescription,
        String munskankarnaReview,
        Rating munskankarnaRating,
        BigDecimal vivinoRating,
        String otherReference,
        String location,
        byte[] image,
        String imageMimeType
) {

    public Wine withQuantity(int newQuantity) {
        return toBuilder().quantity(newQuantity).build();
    }

    public Wine withImage(byte[] newImage, String newImageMimeType) {
        return toBuilder().image(newImage).imageMimeType(newImageMimeType).build();
    }

    public boolean harBild() {
        return image != null && image.length > 0;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id).name(name).wineType(wineType).producer(producer).country(country)
                .region(region).subregion(subregion).grapes(grapes)
                .vintage(vintage).purchaseDate(purchaseDate).price(price).quantity(quantity)
                .purchaseReason(purchaseReason).tastingNotes(tastingNotes).ownRating(ownRating)
                .systembolagetProductNumber(systembolagetProductNumber)
                .systembolagetDescription(systembolagetDescription)
                .munskankarnaReview(munskankarnaReview).munskankarnaRating(munskankarnaRating)
                .vivinoRating(vivinoRating).otherReference(otherReference)
                .location(location).image(image).imageMimeType(imageMimeType);
    }

    public static Builder builder() {
        return new Builder();
    }

    public record WineId(Long value) {
    }

    /**
     * Wine har vuxit till 23 fält (de flesta nullable, ett-till-ett mot
     * Vinlista.xlsx) - en positionell 23-argumentskonstruktor vore
     * oläsbar och felbenägen på anropsplatser. Byggaren är en direkt
     * konsekvens av fältantalet, inte spekulativ ceremoni.
     */
    public static final class Builder {
        private WineId id;
        private String name;
        private WineType wineType;
        private String producer;
        private String country;
        private String region;
        private String subregion;
        private String grapes;
        private int vintage;
        private LocalDate purchaseDate;
        private BigDecimal price;
        private int quantity;
        private String purchaseReason;
        private String tastingNotes;
        private Rating ownRating;
        private String systembolagetProductNumber;
        private String systembolagetDescription;
        private String munskankarnaReview;
        private Rating munskankarnaRating;
        private BigDecimal vivinoRating;
        private String otherReference;
        private String location;
        private byte[] image;
        private String imageMimeType;

        public Builder id(WineId id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder wineType(WineType wineType) {
            this.wineType = wineType;
            return this;
        }

        public Builder producer(String producer) {
            this.producer = producer;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder subregion(String subregion) {
            this.subregion = subregion;
            return this;
        }

        public Builder grapes(String grapes) {
            this.grapes = grapes;
            return this;
        }

        public Builder vintage(int vintage) {
            this.vintage = vintage;
            return this;
        }

        public Builder purchaseDate(LocalDate purchaseDate) {
            this.purchaseDate = purchaseDate;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder purchaseReason(String purchaseReason) {
            this.purchaseReason = purchaseReason;
            return this;
        }

        public Builder tastingNotes(String tastingNotes) {
            this.tastingNotes = tastingNotes;
            return this;
        }

        public Builder ownRating(Rating ownRating) {
            this.ownRating = ownRating;
            return this;
        }

        public Builder systembolagetProductNumber(String systembolagetProductNumber) {
            this.systembolagetProductNumber = systembolagetProductNumber;
            return this;
        }

        public Builder systembolagetDescription(String systembolagetDescription) {
            this.systembolagetDescription = systembolagetDescription;
            return this;
        }

        public Builder munskankarnaReview(String munskankarnaReview) {
            this.munskankarnaReview = munskankarnaReview;
            return this;
        }

        public Builder munskankarnaRating(Rating munskankarnaRating) {
            this.munskankarnaRating = munskankarnaRating;
            return this;
        }

        public Builder vivinoRating(BigDecimal vivinoRating) {
            this.vivinoRating = vivinoRating;
            return this;
        }

        public Builder otherReference(String otherReference) {
            this.otherReference = otherReference;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder image(byte[] image) {
            this.image = image;
            return this;
        }

        public Builder imageMimeType(String imageMimeType) {
            this.imageMimeType = imageMimeType;
            return this;
        }

        public Wine build() {
            return new Wine(id, name, wineType, producer, country, region, subregion, grapes,
                    vintage, purchaseDate, price, quantity, purchaseReason, tastingNotes, ownRating,
                    systembolagetProductNumber, systembolagetDescription, munskankarnaReview, munskankarnaRating,
                    vivinoRating, otherReference, location, image, imageMimeType);
        }
    }
}
