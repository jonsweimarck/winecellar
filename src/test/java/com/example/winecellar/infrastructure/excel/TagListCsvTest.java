package com.example.winecellar.infrastructure.excel;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WINE-51: taggkolumnen i Excel-import/export är en kommaseparerad lista,
 * med standard CSV-citering (citattecken, dubblerat citattecken som
 * escape) för en tagg som själv innehåller ett kommatecken. Testar
 * `format`/`parse` var för sig och som en rundtripp - motsvarande
 * verifiering på hela raden (rätt kolumn) finns i
 * `WineRowParserTest`/`WineRowWriterTest`.
 */
class TagListCsvTest {

    @Test
    void skaFormateraEnEnkelTagg() {
        assertThat(TagListCsv.format(Set.of("Favorit"))).isEqualTo("Favorit");
    }

    @Test
    void skaFormateraFleraTaggarUtanKommatecken() {
        Set<String> tags = new TreeSet<>(Set.of("Favorit", "Vardag"));

        assertThat(TagListCsv.format(tags)).isEqualTo("Favorit, Vardag");
    }

    @Test
    void skaCiteraEnTaggSomInnehållerKommatecken() {
        assertThat(TagListCsv.format(Set.of("Fest, jul"))).isEqualTo("\"Fest, jul\"");
    }

    @Test
    void skaGeNullFörEnTomTagglista() {
        assertThat(TagListCsv.format(Set.of())).isNull();
    }

    @Test
    void skaCiteraOchEscapaEnTaggSomInnehållerCitattecken() {
        assertThat(TagListCsv.format(Set.of("Vinnare av \"Bästa\" 2020")))
                .isEqualTo("\"Vinnare av \"\"Bästa\"\" 2020\"");
    }

    @Test
    void skaTolkaEnEnkelTagg() {
        assertThat(TagListCsv.parse("Favorit")).containsExactly("Favorit");
    }

    @Test
    void skaTolkaFleraTaggarUtanKommatecken() {
        assertThat(TagListCsv.parse("Favorit, Vardag")).containsExactlyInAnyOrder("Favorit", "Vardag");
    }

    @Test
    void skaTolkaEnCiteradTaggSomInnehållerKommatecken() {
        assertThat(TagListCsv.parse("Present, \"Fest, jul\"")).containsExactlyInAnyOrder("Present", "Fest, jul");
    }

    @Test
    void skaTolkaEnCiteradTaggSomInnehållerCitattecken() {
        assertThat(TagListCsv.parse("\"Vinnare av \"\"Bästa\"\" 2020\""))
                .containsExactly("Vinnare av \"Bästa\" 2020");
    }

    @Test
    void skaGeTomMängdFörNull() {
        assertThat(TagListCsv.parse(null)).isEmpty();
    }

    @Test
    void skaGeTomMängdFörBlankText() {
        assertThat(TagListCsv.parse("   ")).isEmpty();
    }

    @Test
    void rundtrippSkaBevaraEnTaggMedKommateckenExakt() {
        Set<String> original = new LinkedHashSet<>(Set.of("Favorit", "Fest, jul", "Present"));

        String formatted = TagListCsv.format(original);
        Set<String> parsed = TagListCsv.parse(formatted);

        assertThat(parsed).isEqualTo(new TreeSet<>(original));
    }

    /**
     * Granskningsfynd, WINE-51 rond 2: explicit täckning för de två
     * kantfallen som tidigare bara verifierats manuellt (inledande/
     * avslutande mellanslag - citeras eftersom en ociterad tagg annars
     * skulle trimmas vid läsning, se `quoteIfNeeded`).
     */
    @Test
    void skaFormateraOchTolkaEnTaggMedInledandeOchAvslutandeMellanslag() {
        Set<String> tags = Set.of(" Favorit ");

        String formatted = TagListCsv.format(tags);

        assertThat(formatted).isEqualTo("\" Favorit \"");
        assertThat(TagListCsv.parse(formatted)).containsExactly(" Favorit ");
    }

    /**
     * Granskningsfynd, WINE-51 rond 2: en tagg som bara är ett enda
     * citattecken - ett minimalt exempel på escape-fallet
     * (dubblerat `""` för ett citattecken inuti en citerad tagg).
     */
    @Test
    void skaFormateraOchTolkaEnTaggSomBaraÄrEttCitattecken() {
        Set<String> tags = Set.of("\"");

        String formatted = TagListCsv.format(tags);

        assertThat(formatted).isEqualTo("\"\"\"\"");
        assertThat(TagListCsv.parse(formatted)).containsExactly("\"");
    }
}
