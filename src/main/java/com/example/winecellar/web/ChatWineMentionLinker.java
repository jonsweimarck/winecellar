package com.example.winecellar.web;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * WINE-56 (se docs/adr/0024-chat-wine-mention-links.md): länkar varje
 * förekomst av ett av den inloggade ägarens FAKTISKA vinnamn i
 * assistentens svar till en exakt namnfacett i vinlistan (samma facett
 * som samlingslänken, se {@link #searchLinkFor}), och avslutar svaret
 * med en samlingslänk till exakt de nämnda vinerna, om minst ett faktiskt
 * nämndes.
 *
 * <p>Matchningen sker mot det redan tolkade syntaxträdet (se
 * {@link ChatMarkdownRenderer#parse}), INTE via strängersättning i den råa
 * markdown-källtexten före parsning - en naiv strängersättning hade riskerat
 * att träffa text som redan ligger inuti en befintlig markdown-länk eller
 * ett kodstycke och förstöra den. Genom att bara besöka {@link Text}-noder
 * som visitorn faktiskt descenderar till (och medvetet INTE descendera in i
 * en redan existerande {@link Link}) skyddas sådan text automatiskt - ett
 * kodstycke ({@code Code}) har ingen egen {@link Text}-barnnod att träffa
 * över huvud taget, dess literal ligger direkt på kodnoden.
 */
final class ChatWineMentionLinker {

    private ChatWineMentionLinker() {
    }

    /**
     * Tolkar `markdown`, länkar varje förekomst av ett namn ur `wineNames`
     * (exakt, skiftlägesokänsligt, ordgränsmedvetet, längsta match vinner
     * vid överlapp) och avslutar med en samlingslänk om minst ett vin
     * nämndes - innan resultatet renderas till säker HTML via
     * {@link ChatMarkdownRenderer#render}.
     */
    static String toHtmlWithWineLinks(String markdown, List<String> wineNames) {
        Node document = ChatMarkdownRenderer.parse(markdown);
        Set<String> mentionedWineNames = linkMentions(document, wineNames);
        if (!mentionedWineNames.isEmpty()) {
            document.appendChild(showWinesParagraph(mentionedWineNames));
        }
        return ChatMarkdownRenderer.render(document);
    }

    /**
     * Går igenom syntaxträdet och slår in varje matchande textbit i en
     * riktig {@link Link}-nod - returnerar de FAKTISKT nämnda vinnamnen
     * (alfabetiskt, skiftlägesokänsligt, dedupliceras automatiskt av
     * {@link TreeSet}).
     */
    private static Set<String> linkMentions(Node document, List<String> wineNames) {
        Set<String> mentionedWineNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<Candidate> candidatesByLengthDescending = wineNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(name -> new Candidate(name, normalizeApostrophes(name)))
                .toList();
        if (candidatesByLengthDescending.isEmpty()) {
            return mentionedWineNames;
        }
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                // Descendera medvetet INTE - text som redan ligger i en
                // befintlig länk ska inte länkas om (skulle ge en trasig,
                // nästlad länk).
            }

            @Override
            public void visit(Text text) {
                linkMentionsInTextNode(text, candidatesByLengthDescending, mentionedWineNames);
            }
        });
        return mentionedWineNames;
    }

    /**
     * Ersätter `textNode` med en följd av syskon (omatchad text, en
     * {@link Link} per matchning, omatchad text ...) om den innehåller
     * minst en matchning - annars orörd.
     */
    private static void linkMentionsInTextNode(
            Text textNode, List<Candidate> candidatesByLengthDescending, Set<String> mentionedWineNames) {
        String literal = textNode.getLiteral();
        List<Match> matches = findMatches(literal, candidatesByLengthDescending);
        if (matches.isEmpty()) {
            return;
        }
        Node insertionPoint = textNode;
        int cursor = 0;
        for (Match match : matches) {
            if (match.start() > cursor) {
                Node before = new Text(literal.substring(cursor, match.start()));
                insertionPoint.insertAfter(before);
                insertionPoint = before;
            }
            Link link = new Link(searchLinkFor(match.wineName()), null);
            link.appendChild(new Text(literal.substring(match.start(), match.end())));
            insertionPoint.insertAfter(link);
            insertionPoint = link;
            mentionedWineNames.add(match.wineName());
            cursor = match.end();
        }
        if (cursor < literal.length()) {
            insertionPoint.insertAfter(new Text(literal.substring(cursor)));
        }
        textNode.unlink();
    }

    private record Match(int start, int end, String wineName) {
    }

    /**
     * En kandidat i sin FAKTISKA, databaslagrade form (`name`, används för
     * matchningens länkmål och den returnerade `mentionedWineNames`) och en
     * apostroftolerant kopia (`normalizedName`, används BARA för själva
     * jämförelsen) - se {@link #normalizeApostrophes}.
     */
    private record Candidate(String name, String normalizedName) {
    }

    /**
     * Hittar samtliga icke-överlappande matchningar i `text` - vid varje
     * startposition provas kandidaterna längst först (listan är redan
     * sorterad efter fallande längd), så en längre, mer specifik fras
     * vinner alltid över en kortare delsträng av samma fras (t.ex.
     * "Château Margaux" framför "Margaux"). Ordgränsmedveten: en matchning
     * får inte börja eller sluta mitt i ett ord.
     *
     * <p>Jämförelsen sker mot en apostroftolerant, normaliserad kopia av
     * `text` (se {@link #normalizeApostrophes}) - en LLM-genererad svarstext
     * skriver ofta en typografisk apostrof (t.ex. U+2019) även där
     * databasens vinnamn har en rak apostrof (U+0027), eller tvärtom.
     * Normaliseringen ersätter varje apostrofvariant med EXAKT ett tecken,
     * så längden - och därmed varje positions {@code start}/{@code end} -
     * är identisk med originaltexten; den text som faktiskt visas i länken
     * ({@link #linkMentionsInTextNode}) hämtas alltid ur den ONORMALISERADE
     * `literal`-strängen, så assistentens ordagranna formulering syns
     * oförändrad i svaret. Ordgränskontrollen ({@link #isMentionBoundary})
     * körs mot den onormaliserade `text`-strängen, men behandlar själv varje
     * apostrofvariant som ett icke-ordtecken oavsett dess egen Unicode-
     * kategori - `ʼ` (U+02BC, "modifier letter apostrophe") klassas annars
     * av {@link Character#isLetterOrDigit(int)} som en bokstav (kategori
     * "Letter, modifier"), till skillnad från de övriga fem varianterna, och
     * hade annars kunnat få en gräns precis intill den att felaktigt räknas
     * som "mitt i ett ord".
     */
    private static List<Match> findMatches(String text, List<Candidate> candidatesByLengthDescending) {
        String normalizedText = normalizeApostrophes(text);
        List<Match> matches = new ArrayList<>();
        int length = text.length();
        int i = 0;
        outer:
        while (i < length) {
            for (Candidate candidate : candidatesByLengthDescending) {
                int candidateLength = candidate.normalizedName().length();
                if (i + candidateLength <= length
                        && normalizedText.regionMatches(true, i, candidate.normalizedName(), 0, candidateLength)
                        && isMentionBoundary(text, i)
                        && isMentionBoundary(text, i + candidateLength)) {
                    matches.add(new Match(i, i + candidateLength, candidate.name()));
                    i += candidateLength;
                    continue outer;
                }
            }
            i++;
        }
        return matches;
    }

    /**
     * Ersätter varje vanlig apostrofvariant (rak `'` U+0027, typografiska
     * `’` U+2019/`‘` U+2018/`‛` U+201B, "modifier letter apostrophe"
     * `ʼ` U+02BC, "prime" `′` U+2032) med en gemensam, kanonisk form (den
     * raka apostrofen) - EN tecken-för-tecken-ersättning, så resultatet
     * alltid har SAMMA längd som `value`. Medvetet en snäv, uppräknad
     * grupp - inte ett brett Unicode-normaliseringsschema (NFKD/NFC etc.),
     * som hade normaliserat betydligt mer än bara apostroftecken.
     */
    private static String normalizeApostrophes(String value) {
        StringBuilder normalized = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isApostropheVariant(c)) {
                if (normalized == null) {
                    normalized = new StringBuilder(value);
                }
                normalized.setCharAt(i, '\'');
            }
        }
        return normalized == null ? value : normalized.toString();
    }

    private static boolean isApostropheVariant(char c) {
        return switch (c) {
            case '\'', '’', '‘', '‛', 'ʼ', '′' -> true;
            default -> false;
        };
    }

    private static boolean isMentionBoundary(String text, int index) {
        boolean beforeIsWordChar = index > 0 && isWordCodePoint(text.codePointBefore(index));
        boolean afterIsWordChar = index < text.length() && isWordCodePoint(text.codePointAt(index));
        return !(beforeIsWordChar && afterIsWordChar);
    }

    /**
     * Som {@link Character#isLetterOrDigit(int)}, men behandlar en
     * apostrofvariant (se {@link #isApostropheVariant}) som ett icke-
     * ordtecken oavsett dess egen Unicode-kategori - `ʼ` (U+02BC) klassas
     * annars som en bokstav ("Letter, modifier"), till skillnad från de
     * andra fem apostrofvarianterna, vilket annars gett en inkonsekvent
     * ordgränsbedömning just för den varianten.
     */
    private static boolean isWordCodePoint(int codePoint) {
        if (codePoint <= Character.MAX_VALUE && isApostropheVariant((char) codePoint)) {
            return false;
        }
        return Character.isLetterOrDigit(codePoint);
    }

    /**
     * `reset=true` TILLSAMMANS med en `name`-parameter (samma exakta facett
     * som {@link #showWinesLinkFor} redan använder för samlingslänken) -
     * annars kan ett redan aktivt, ihågkommet filter (t.ex. vintyp, se
     * WINE-55/ADR 0023) dölja det enskilda nämnda vinet efter klick.
     *
     * <p>Byggd om från en fritextsökning (`search`) till samma `name`-facett
     * som samlingslänken (buggfynd efter merge, WINE-56): vinnamnet är redan
     * känt EXAKT (hämtat direkt ur den inloggade ägarens egen kandidatlista,
     * se {@link #linkMentions}), så det finns ingen anledning att gå via en
     * bredare, ordstammad fritextsökning - som dessutom visade sig ge en
     * missvisande, "+"-uppdelad filterchip (`WineController#
     * searchTermLabel`, en etikett avsedd för det allmänna sökfältets
     * OCH-mellan-orden-semantik, inte för ett redan känt, exakt vinnamn).
     * `name`-facetten ger samma resultat men en ren, konsekvent chip -
     * identisk med samlingslänkens.
     */
    private static String searchLinkFor(String wineName) {
        return UriComponentsBuilder.fromPath("/")
                .queryParam("reset", "true")
                .queryParam("name", wineName)
                .build().encode().toUriString();
    }

    /**
     * Samlingslänken sist i svaret - `reset=true` TILLSAMMANS med en
     * `name`-parameter per nämnt vin (se WineController#resolveFilter,
     * granskningsfynd WINE-56) rensar aktivt filter och visar exakt de
     * nämnda vinerna, oavsett vad som råkade vara filtrerat sedan innan.
     */
    private static Node showWinesParagraph(Collection<String> mentionedWineNames) {
        Paragraph paragraph = new Paragraph();
        Link link = new Link(showWinesLinkFor(mentionedWineNames), null);
        link.appendChild(new Text("Visa dessa viner i vinlistan"));
        paragraph.appendChild(link);
        return paragraph;
    }

    private static String showWinesLinkFor(Collection<String> mentionedWineNames) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/").queryParam("reset", "true");
        for (String wineName : mentionedWineNames) {
            builder.queryParam("name", wineName);
        }
        return builder.build().encode().toUriString();
    }
}
