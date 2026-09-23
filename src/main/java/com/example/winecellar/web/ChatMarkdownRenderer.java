package com.example.winecellar.web;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

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
 * länkscheman (t.ex. {@code javascript:}) i markdown-länkar/-bilder via
 * CommonMarks egen {@code DefaultUrlSanitizer}.
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
            .build();

    private ChatMarkdownRenderer() {
    }

    static String toSafeHtml(String markdown) {
        Node document = PARSER.parse(markdown);
        return RENDERER.render(document);
    }
}
