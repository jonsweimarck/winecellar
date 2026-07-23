package com.example.winecellar.application;

import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;

import java.util.Comparator;
import java.util.function.Function;

/**
 * De fält vinlistan kan sorteras på. Varje konstant bygger sin egen
 * comparator() från en fältutläsare (t.ex. Wine::name) plus en "stigande
 * ordning"-comparator för fältets typ - Rating (inte Comparable) får en
 * egen (se RATING_ASCENDING), övriga fält använder Comparator.naturalOrder().
 *
 * Riktning (stigande/fallande) vänder bara på jämförelsen av faktiska
 * värden, INTE på null-hanteringen - null (t.ex. inget pris/betyg satt)
 * ska alltid hamna sist, oavsett riktning. Att i stället bygga
 * "nullsLast(ascendingOrder).reversed()" hade fått null-värden att hoppa
 * till TOPPEN vid fallande sortering (reversed() vänder på hela
 * jämförelsen, inklusive nullsLast-logiken) - se withDirection(), som
 * medvetet applicerar nullsLast utanpå den redan riktningsvända
 * värdejämförelsen istället.
 */
public enum SortField {
    NAME("Namn") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::name, String.CASE_INSENSITIVE_ORDER, direction);
        }
    },
    PRODUCER("Producent") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::producer, String.CASE_INSENSITIVE_ORDER, direction);
        }
    },
    COUNTRY("Land") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::country, String.CASE_INSENSITIVE_ORDER, direction);
        }
    },
    VINTAGE("Årgång") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::vintage, Comparator.naturalOrder(), direction);
        }
    },
    QUANTITY("Antal flaskor") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::quantity, Comparator.naturalOrder(), direction);
        }
    },
    PRICE("Pris") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::price, Comparator.naturalOrder(), direction);
        }
    },
    PURCHASE_DATE("Inköpsdatum") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::purchaseDate, Comparator.naturalOrder(), direction);
        }
    },
    OWN_RATING("Eget betyg") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::ownRating, RATING_ASCENDING, direction);
        }
    },
    MUNSKANKARNA_RATING("Munskänkarnas betyg") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::munskankarnaRating, RATING_ASCENDING, direction);
        }
    },
    VIVINO_RATING("Vivino-betyg") {
        public Comparator<Wine> comparator(SortDirection direction) {
            return withDirection(Wine::vivinoRating, Comparator.naturalOrder(), direction);
        }
    };

    /**
     * Rating deklareras i FALLANDE betygsordning (R20 bäst ... R6 sämst,
     * se Rating.java) - ordinal() ger alltså LÄGST tal för det BÄSTA
     * betyget. "Stigande" ska betyda stigande betygsVÄRDE (sämst->bäst,
     * dvs 6 före 20), vilket är samma sak som fallande ordinal - därav
     * reversed(). Utan detta hade en naiv strängjämförelse av etiketten
     * (t.ex. "10 ..." mot "9 ...") dessutom gett fel resultat rakt av,
     * eftersom "10" < "9" bokstavsordning men 10 > 9 betygsmässigt.
     */
    private static final Comparator<Rating> RATING_ASCENDING = Comparator.comparing(Rating::ordinal).reversed();

    private final String label;

    SortField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public abstract Comparator<Wine> comparator(SortDirection direction);

    public static SortField fromLabel(String label) {
        for (SortField field : values()) {
            if (field.label.equalsIgnoreCase(label)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Okänt sorteringsfält: \"" + label + "\"");
    }

    private static <T> Comparator<Wine> withDirection(Function<Wine, T> fieldValue, Comparator<T> ascendingOrder, SortDirection direction) {
        Comparator<T> comparison = direction == SortDirection.DESCENDING ? ascendingOrder.reversed() : ascendingOrder;
        return Comparator.comparing(fieldValue, Comparator.nullsLast(comparison));
    }
}
