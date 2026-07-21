package com.example.winecellar.application;

import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;

import java.util.Comparator;
import java.util.function.Function;

/**
 * De fält vinlistan kan sorteras på. Varje konstant bygger sin egen
 * comparator() från en fältutläsare (t.ex. Wine::name) plus en "stigande
 * ordning"-comparator för fältets typ - Rating (inte Comparable) får en
 * egen (se BETYG_STIGANDE), övriga fält använder Comparator.naturalOrder().
 *
 * Riktning (stigande/fallande) vänder bara på jämförelsen av faktiska
 * värden, INTE på null-hanteringen - null (t.ex. inget pris/betyg satt)
 * ska alltid hamna sist, oavsett riktning. Att i stället bygga
 * "nullsLast(stigandeOrdning).reversed()" hade fått null-värden att hoppa
 * till TOPPEN vid fallande sortering (reversed() vänder på hela
 * jämförelsen, inklusive nullsLast-logiken) - se medRiktning(), som
 * medvetet applicerar nullsLast utanpå den redan riktningsvända
 * värdejämförelsen istället.
 */
public enum Sorteringsfält {
    NAMN("Namn") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::name, String.CASE_INSENSITIVE_ORDER, riktning);
        }
    },
    PRODUCENT("Producent") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::producer, String.CASE_INSENSITIVE_ORDER, riktning);
        }
    },
    LAND("Land") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::country, String.CASE_INSENSITIVE_ORDER, riktning);
        }
    },
    ARGANG("Årgång") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::vintage, Comparator.naturalOrder(), riktning);
        }
    },
    ANTAL("Antal flaskor") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::quantity, Comparator.naturalOrder(), riktning);
        }
    },
    PRIS("Pris") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::price, Comparator.naturalOrder(), riktning);
        }
    },
    INKOPSDATUM("Inköpsdatum") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::purchaseDate, Comparator.naturalOrder(), riktning);
        }
    },
    EGET_BETYG("Eget betyg") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::ownRating, BETYG_STIGANDE, riktning);
        }
    },
    MUNSKANKARNA_BETYG("Munskänkarnas betyg") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::munskankarnaRating, BETYG_STIGANDE, riktning);
        }
    },
    VIVINO_BETYG("Vivino-betyg") {
        public Comparator<Wine> comparator(SorteringsRiktning riktning) {
            return medRiktning(Wine::vivinoRating, Comparator.naturalOrder(), riktning);
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
    private static final Comparator<Rating> BETYG_STIGANDE = Comparator.comparing(Rating::ordinal).reversed();

    private final String etikett;

    Sorteringsfält(String etikett) {
        this.etikett = etikett;
    }

    public String etikett() {
        return etikett;
    }

    public abstract Comparator<Wine> comparator(SorteringsRiktning riktning);

    public static Sorteringsfält frånEtikett(String etikett) {
        for (Sorteringsfält fält : values()) {
            if (fält.etikett.equalsIgnoreCase(etikett)) {
                return fält;
            }
        }
        throw new IllegalArgumentException("Okänt sorteringsfält: \"" + etikett + "\"");
    }

    private static <T> Comparator<Wine> medRiktning(Function<Wine, T> fältvärde, Comparator<T> stigandeOrdning, SorteringsRiktning riktning) {
        Comparator<T> jämförelse = riktning == SorteringsRiktning.FALLANDE ? stigandeOrdning.reversed() : stigandeOrdning;
        return Comparator.comparing(fältvärde, Comparator.nullsLast(jämförelse));
    }
}
