package com.example.winecellar.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enhetstest för markdown-till-HTML-tolkningen (WINE-53) - både att vanlig
 * markdown-syntax faktiskt tolkas (rubrik, fetstil, lista) och att källtexten
 * behandlas som opålitlig indata (rå HTML/farliga länkscheman saneras bort),
 * se klasskommentaren i {@link ChatMarkdownRenderer}.
 */
class ChatMarkdownRendererTest {

    @Test
    void skaTolkaRubrikSomRiktigHtmlRubrik() {
        String html = ChatMarkdownRenderer.toSafeHtml("# Ett vinförslag");

        assertThat(html).contains("<h1>Ett vinförslag</h1>");
    }

    @Test
    void skaTolkaFetstil() {
        String html = ChatMarkdownRenderer.toSafeHtml("Prova en **Barolo** till rödkött.");

        assertThat(html).contains("<strong>Barolo</strong>");
    }

    @Test
    void skaTolkaPunktlista() {
        String html = ChatMarkdownRenderer.toSafeHtml("Förslag:\n- Barolo\n- Chianti");

        assertThat(html).contains("<ul>").contains("<li>Barolo</li>").contains("<li>Chianti</li>");
    }

    @Test
    void skaTolkaKodblock() {
        String html = ChatMarkdownRenderer.toSafeHtml("```\nSELECT * FROM wines;\n```");

        assertThat(html).contains("<pre>").contains("SELECT * FROM wines;");
    }

    @Test
    void skaEscapaRåHtmlIKälltextenIStälletFörAttSläppaIgenomDenSomKörbarHtml() {
        String html = ChatMarkdownRenderer.toSafeHtml("Se detta: <script>alert('xss')</script>");

        assertThat(html)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void skaSaneraBortEttFarligtLänkschemaITolkadeLänkar() {
        String html = ChatMarkdownRenderer.toSafeHtml("[Klicka här](javascript:alert('xss'))");

        assertThat(html).doesNotContain("href=\"javascript:");
    }

    @Test
    void skaBehållaEttOfarligtLänkschema() {
        String html = ChatMarkdownRenderer.toSafeHtml("[Systembolaget](https://www.systembolaget.se)");

        assertThat(html).contains("href=\"https://www.systembolaget.se\"");
    }

    @Test
    void skaSaneraBortEnDataUriLänk() {
        // CommonMarks DefaultUrlSanitizer tillåter annars data:-scheman - en
        // svag nätfiskevektor (t.ex. data:text/html,<falsk inloggningssida>),
        // se klasskommentaren i ChatMarkdownRenderer.
        String html = ChatMarkdownRenderer.toSafeHtml(
                "[Klicka här](data:text/html,<script>alert('xss')</script>)");

        assertThat(html).doesNotContainIgnoringCase("data:");
    }

    @Test
    void skaSaneraBortEnDataUriBild() {
        String html = ChatMarkdownRenderer.toSafeHtml("![Etikett](data:image/png;base64,AAAA)");

        assertThat(html).doesNotContainIgnoringCase("data:");
    }

    @Test
    void skaBehållaEnRelativLänkUtanSchemaOförändrad() {
        // WINE-56: ChatWineMentionLinker bygger vinlistelänkar som
        // relativa URL:er (t.ex. /?search=Barolo) - CommonMarks
        // DefaultUrlSanitizer (som NoDataUrlSanitizer delegerar till,
        // se klasskommentaren) tillåter schemalösa/relativa länkar,
        // blockerar bara farliga SCHEMAN. En förutsättning som verifieras
        // explicit här, inte bara antas, eftersom den är säkerhetsrelevant
        // för hela vinnamnslänkningen.
        String html = ChatMarkdownRenderer.toSafeHtml("[Barolo](/?search=Barolo)");

        assertThat(html).contains("href=\"/?search=Barolo\"");
    }

    @Test
    void skaRenderaEnMjukRadbrytningUtanBlankradSomEnRiktigBrTagg() {
        // Granskningsfynd (samma story): en enskild \n utan blankrad
        // (CommonMarks "soft break") renderas annars som en bokstavlig \n
        // rakt i HTML-källkoden, vilket webbläsarens white-space: normal
        // (.meddelande-innehall, chatt.html) kollapsar till ett mellanslag -
        // flera på varandra följande textrader (ett mycket troligt
        // LLM-svarsmönster, t.ex. ett vinförslag per rad utan
        // markdown-punktlista) hade då visats som EN sammanhängande mening.
        // Se klasskommentaren i ChatMarkdownRenderer.
        String html = ChatMarkdownRenderer.toSafeHtml("Rad 1\nRad 2\nRad 3");

        assertThat(html)
                .contains("Rad 1<br")
                .contains("Rad 2<br")
                .contains("Rad 3");
    }
}
