package com.example.winecellar.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enhetstest för vinnamnslänkningen i assistentens svar (WINE-56, se
 * docs/adr/0024-chat-wine-mention-links.md) - matchningen mot den inloggade
 * ägarens faktiska vinnamn, AST-baserad (inte strängersättning i
 * markdown-källan) skydd av redan befintlig markup, och samlingslänken sist
 * i svaret. Samma mönster som {@link ChatMarkdownRendererTest} - ett rent
 * enhetstest av matchningslogiken, oberoende av {@link ChatController}.
 *
 * <p>Länkar renderas med {@code rel="nofollow"} FÖRE {@code href} -
 * CommonMarks egen, redan befintliga konsekvens av att
 * {@link ChatMarkdownRenderer} sätter {@code sanitizeUrls(true)} (se
 * klasskommentaren där) - {@link #linkContaining} bygger assertions som
 * inte är beroende av den exakta attributordningen.
 */
class ChatWineMentionLinkerTest {

    @Test
    void skaLänkaEttNämntVinnamnTillEnNamnsökning() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Prova en Barolo till rödkött.", List.of("Barolo"));

        assertThat(html).contains(linkContaining("/?reset=true&amp;search=Barolo", "Barolo"));
    }

    @Test
    void skaLänkaSammaVinnamnVidVarjeFörekomst() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Barolo passar bra till rödkött. Barolo passar även till lagrad ost.",
                List.of("Barolo"));

        String länk = linkContaining("/?reset=true&amp;search=Barolo", "Barolo");
        assertThat(html)
                .contains(länk + " passar bra")
                .contains(länk + " passar även");
    }

    @Test
    void skaInteLänkaTextSomInteMatcharNågotAvÄgarensVinnamn() {
        // "barolone" innehåller "Barolo" som delsträng, men är inte samma
        // ord - ordgränsmedveten matchning ska inte länka mitt i ordet.
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Ett helt orelaterat ord: barolone.", List.of("Barolo"));

        assertThat(html).doesNotContain("href=");
    }

    @Test
    void skaInteGeFalskaTräffarMotAndraAnvändaresViner() {
        // wineNames representerar bara den inloggade ägarens EGNA viner
        // (se ChatController#show) - ett namn som inte finns i den listan
        // ska aldrig länkas, oavsett vad texten innehåller.
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Chablis är ett klassiskt vitt vin.", List.of("Barolo"));

        assertThat(html).doesNotContain("href=");
    }

    @Test
    void skaLänkaDenLängstaMestSpecifikaFrasenVidÖverlapp() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Prova en Château Margaux 2015.", List.of("Margaux", "Château Margaux"));

        assertThat(html)
                .contains(linkContaining("/?reset=true&amp;search=Ch%C3%A2teau%20Margaux", "Château Margaux"))
                .doesNotContain("search=Margaux\"");
    }

    @Test
    void skaInteLänkaOmTextenRedanLiggerIEnBefintligMarkdownLänk() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "[Barolo](https://example.com/barolo)", List.of("Barolo"));

        assertThat(html)
                .contains(linkContaining("https://example.com/barolo", "Barolo"))
                .doesNotContain("/?search=Barolo");
    }

    @Test
    void skaInteLänkaOmTextenLiggerIEttKodstycke() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Kör `SELECT * FROM Barolo` för att se detaljerna.", List.of("Barolo"));

        assertThat(html)
                .contains("<code>SELECT * FROM Barolo</code>")
                .doesNotContain("/?search=Barolo");
    }

    @Test
    void skaAvslutaSvaretMedEnSamlingslänkOmMinstEttVinNämns() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Prova en Barolo.", List.of("Barolo", "Chablis"));

        assertThat(html).contains(linkContaining("/?reset=true&amp;name=Barolo", "Visa dessa viner i vinlistan"));
    }

    @Test
    void skaInteAvslutaSvaretMedNågonSamlingslänkOmIngetVinNämns() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Jag har inga direkta förslag just nu.", List.of("Barolo", "Chablis"));

        assertThat(html).doesNotContain("Visa dessa viner i vinlistan");
    }

    @Test
    void skaBaraRäknaEttNämntVinEnGångISamlingslänkenÄvenOmDetNämnsFleraGånger() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Barolo passar bra. Faktum är att Barolo är ett utmärkt val.",
                List.of("Barolo"));

        assertThat(html)
                .contains(linkContaining("/?reset=true&amp;name=Barolo", "Visa dessa viner i vinlistan"))
                .doesNotContain("name=Barolo&amp;name=Barolo");
    }

    @Test
    void skaInkluderaSamtligaNämndaVinIDenKombineradeSamlingslänken() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Antingen en Barolo eller en Chablis passar bra.", List.of("Barolo", "Chablis"));

        assertThat(html).contains(
                linkContaining("/?reset=true&amp;name=Barolo&amp;name=Chablis", "Visa dessa viner i vinlistan"));
    }

    @Test
    void skaProcentkodaSärskildaTeckenIVinnamnetIStälletFörAttInjiceraEnExtraQueryparameter() {
        // "&" skulle, om det lämnades okodat i länken, brutit av search-
        // värdet och startat en ny (falsk) queryparameter - "1 & 2"
        // används medvetet, inte bara ett bokstavligt "&", för att bevisa
        // att RESTEN av namnet efter tecknet inte tappas bort.
        String html = ChatWineMentionLinker.toHtmlWithWineLinks(
                "Prova gärna Vin 1 & 2 till maten.", List.of("Vin 1 & 2"));

        assertThat(html)
                .contains(linkContaining("/?reset=true&amp;search=Vin%201%20%26%202", "Vin 1 &amp; 2"))
                .doesNotContain("search=Vin%201%20\"")
                .doesNotContain("&amp;2=");
    }

    @Test
    void skaInteLänkaNågotOmÄgarenIngaVinerHar() {
        String html = ChatWineMentionLinker.toHtmlWithWineLinks("Prova en Barolo.", List.of());

        assertThat(html)
                .doesNotContain("href=")
                .contains("Prova en Barolo.");
    }

    private static String linkContaining(String href, String linkText) {
        return "href=\"" + href + "\">" + linkText + "</a>";
    }
}
