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
 */
final class ChatMarkdownRenderer {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .urlSanitizer(new NoDataUrlSanitizer())
            .build();

    private ChatMarkdownRenderer() {
    }

    static String toSafeHtml(String markdown) {
        Node document = PARSER.parse(markdown);
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
