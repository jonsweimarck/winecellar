package com.example.winecellar.domain;

/**
 * `image`s generererade equals()/hashCode() jämför referens, inte innehåll -
 * en Records-egenhet för array-komponenter. Två Wine-instanser med olikt
 * array-objekt men identiska bytes räknas alltså som olika. Jämför
 * {@code image()}-innehåll explicit (t.ex. Arrays.equals) om det behövs,
 * lita inte på Wine.equals() för det fältet.
 */
public record Wine(
        WineId id,
        String name,
        WineType wineType,
        String producer,
        String country,
        int vintage,
        int quantity,
        String location,
        byte[] image,
        String imageMimeType
) {

    public Wine withQuantity(int newQuantity) {
        return new Wine(id, name, wineType, producer, country, vintage, newQuantity, location, image, imageMimeType);
    }

    public Wine withImage(byte[] newImage, String newImageMimeType) {
        return new Wine(id, name, wineType, producer, country, vintage, quantity, location, newImage, newImageMimeType);
    }

    public boolean harBild() {
        return image != null && image.length > 0;
    }

    public record WineId(Long value) {
    }
}
