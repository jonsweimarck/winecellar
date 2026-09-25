package com.example.winecellar.web;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.DefaultUrlSanitizer;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.UrlSanitizer;

/**
 * Renderar chattassistentens fria textsvar (WINE-53) - de kommer kodade som
 * markdown-syntax (rubriker, listor, fetstil, kod, m.m.) från Anthropics
 * API, se {@link com.example.winecellar.application.WineChatAssistant#reply}
 * - till HTML för visning i {@code chatt.html}.
 *
 * <p>Källtexten är i grunden opålitlig indata (en extern tjänsts textsvar,
 * i teorin även påverkbar av en användares egna chattmeddelanden via
 * prompt-injektion) och måste behandlas därefter - CommonMark-
 * specifikationen tillåter annars rå HTML i källtexten att passera rakt
 * igenom till utdatan oförändrad (t.ex. en bokstavlig {@code <script>}-tagg
 * i svaret). {@code escapeHtml(true)} stänger av just det genomsläppet
 * (rå HTML i källan blir escapad text i stället för körbar HTML - den
 * riktiga HTML:en renderaren själv genererar för rubriker/listor/fetstil
 * osv. påverkas inte). {@code sanitizeUrls(true)} spärrar farliga
 * länkscheman (t.ex. {@code javascript:}) i markdown-länkar/-bilder.
 *
 * <p>Sätter dessutom en egen {@link NoDataUrlSanitizer} i stället för att
 * lita på CommonMarks {@link DefaultUrlSanitizer} rakt av - den tillåter
 * annars även {@code data:}-scheman (rimligt för ett allmänt
 * markdown-bibliotek, t.ex. inbäddade base64-bilder), vilket en oberoende
 * granskning (efter att den ursprungliga XSS-hårdningen redan var
 * verifierad) pekade ut som en kvarvarande, om än svag,
 * nätfiskevektor (t.ex. {@code data:text/html,<falsk inloggningssida>}
 * öppnad i webbläsarens egen, unika opaka origin - ingen session-/
 * XSS-risk, men ändå möjlig att undvika helt). Den här chatten renderar
 * aldrig några legitima bilder (assistenten är rent textbaserad), så det
 * finns ingen motsvarande legitim användning av {@code data:} att väga
 * mot - schemat spärras därför helt, för både länkar och bilder.
 *
 * <p>Används bara för assistentens (ASSISTANT-roll) meddelanden - se
 * {@link ChatController} - inte användarens egna, som förblir ren,
 * escapad text (samma beteende som innan WINE-53).
 *
 * <p><b>{@code softbreak("<br />\n")} - granskningsfynd, samma story.</b>
 * CommonMark renderar annars en "soft break" (en enskild {@code \n} som
 * INTE är separerad av en blankrad - ett mycket troligt LLM-svarsmönster,
 * t.ex. flera vinförslag, ett per rad, utan markdown-punktlista; systemprompten
 * i {@code AnthropicWineChatAssistant} instruerar inte modellen om någon
 * specifik radbrytningskonvention) som en bokstavlig {@code \n} rakt i
 * HTML-källkoden, INTE som {@code <br>}. Innan WINE-53 garanterade
 * {@code white-space: pre-wrap} på hela {@code .meddelande} att varje
 * radbrytning förblev synlig oavsett formatering - {@code
 * .meddelande-innehall} (chatt.html) saknar den regeln (se klasskommentaren
 * i chatt.html för varför), så webbläsarens standard {@code white-space:
 * normal} annars hade kollapsat flera på varandra följande textrader till
 * EN sammanhängande mening. Att explicit rendera soft breaks som riktiga
 * {@code <br>}-taggar återställer garantin utan att sätta {@code pre-wrap}
 * (som i stället hade gjort whitespace MELLAN riktiga block-element som
 * {@code <p>}/{@code <ul>} synligt som oavsiktliga extra blankrader, se
 * samma klasskommentar i chatt.html).
 */
final class ChatMarkdownRenderer {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .urlSanitizer(new NoDataUrlSanitizer())
            .softbreak("<br />\n")
            .build();

    private ChatMarkdownRenderer() {
    }

    static String toSafeHtml(String markdown) {
        return render(parse(markdown));
    }

    /**
     * Uppdelad form av {@link #toSafeHtml} (WINE-56) - {@link
     * ChatWineMentionLinker} manipulerar det tolkade syntaxträdet (länkar
     * vinnamn, se docs/adr/0024-chat-wine-mention-links.md) MELLAN parsning
     * och rendering, och återanvänder därför den här klassens delade
     * {@link Parser}/{@link HtmlRenderer}-konfiguration i stället för att
     * bygga en egen, andra uppsättning med samma säkerhetsinställningar som
     * riskerar att drifta isär.
     */
    static Node parse(String markdown) {
        return PARSER.parse(markdown);
    }

    static String render(Node document) {
        return RENDERER.render(document);
    }

    /**
     * Slår i övrigt igen till CommonMarks {@link DefaultUrlSanitizer}
     * (http/https/mailto), men spärrar dessutom {@code data:}-scheman -
     * se klasskommentaren ovan för varför.
     */
    private static final class NoDataUrlSanitizer implements UrlSanitizer {

        private static final String DATA_SCHEME_PREFIX = "data:";

        private final UrlSanitizer delegate = new DefaultUrlSanitizer();

        @Override
        public String sanitizeLinkUrl(String url) {
            return blockDataScheme(delegate.sanitizeLinkUrl(url));
        }

        @Override
        public String sanitizeImageUrl(String url) {
            return blockDataScheme(delegate.sanitizeImageUrl(url));
        }

        private static String blockDataScheme(String sanitizedUrl) {
            boolean isDataUrl = sanitizedUrl.regionMatches(
                    true, 0, DATA_SCHEME_PREFIX, 0, DATA_SCHEME_PREFIX.length());
            return isDataUrl ? "" : sanitizedUrl;
        }
    }
}
