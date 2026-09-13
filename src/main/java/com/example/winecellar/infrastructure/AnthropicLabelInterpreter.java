package com.example.winecellar.infrastructure;

import com.example.winecellar.application.InterpretedLabel;
import com.example.winecellar.application.LabelInterpreter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Anropar Anthropics Messages API direkt via RestClient - inte den
 * officiella Anthropic Java-SDK:n, för att slippa en ny beroende i den
 * deployade jaren för den här enda anropstypen (se docs/adr/0012-
 * label-scanning-llm-integration.md). API-nyckel/modell kommer från
 * miljövariabler, samma konfigurationsmönster som resten av appen
 * använder för hemligheter.
 */
@Component
public class AnthropicLabelInterpreter implements LabelInterpreter {

    // Tillfällig diagnosloggning (felsökning av WINECELLAR_ANTHROPIC_MODEL,
    // 2026-09) - interpret() svalde tidigare ALLA undantag helt tyst (se
    // Optional.empty()-konventionen nedan), vilket gjorde ett avvisat
    // modell-id från Anthropics API omöjligt att skilja från ett
    // nätverksfel eller ett tomt svar. Ta bort igen när felsökningen är
    // klar - den hör inte till den ursprungliga "misslyckas tyst"-designen
    // i ADR 0012.
    private static final Logger log = LoggerFactory.getLogger(AnthropicLabelInterpreter.class);

    /**
     * name/producer/vintage FÅR INTE gissas eller härledas - bara läsas
     * rakt av. country/region FÅR härledas (appellation, språk, druvsort
     * osv.) om de inte står utskrivna - se WINE-5.
     */
    private static final String PROMPT = """
            Du tittar på ett foto av en vinetikett. Läs ut följande fält om de går att
            läsa eller härleda, annars null:
            - name: vinets namn. Får ALDRIG gissas eller härledas - bara om det faktiskt
              går att läsa på etiketten.
            - producer: producentens namn. Får ALDRIG gissas eller härledas - bara om det
              faktiskt går att läsa på etiketten.
            - vintage: årgång som ett heltal. Får ALDRIG gissas eller härledas - bara om
              det faktiskt går att läsa på etiketten.
            - country: ursprungsland. Får härledas från övrig information på etiketten
              (t.ex. appellation, språk, druvsort) om det inte står utskrivet.
            - region: ursprungsregion. Får härledas på samma sätt som country.

            Svara ENDAST med ett JSON-objekt med exakt dessa fem nycklar, inget annat
            (ingen förklarande text, inga markdown-kodstaket). Sätt null för fält som
            inte kan läsas eller härledas. Exempel:
            {"name": "Barolo", "producer": "Pio Cesare", "vintage": 2018, "country": "Italien", "region": "Piemonte"}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;

    public AnthropicLabelInterpreter(
            RestClient.Builder restClientBuilder,
            @Value("${winecellar.anthropic.api-key}") String apiKey,
            @Value("${winecellar.anthropic.model}") String model) {
        this.model = model;
        this.restClient = AnthropicApiClient.build(restClientBuilder, apiKey);
    }

    @Override
    public Optional<InterpretedLabel> interpret(byte[] imageData, String mimeType) {
        try {
            return parseInterpretedLabel(extractResponseText(callAnthropic(imageData, mimeType)));
        } catch (RestClientResponseException e) {
            // RestClientResponseException (HttpClientErrorException/
            // HttpServerErrorException) bär Anthropics egen felrespons i
            // klartext - t.ex. "model: not_found_error" om modell-id:t inte
            // finns. Just den kroppen är annars osynlig - e.getMessage()
            // ensamt visar bara statustexten, inte VARFÖR.
            log.warn("Etikettolkning mot Anthropics API misslyckades (modell: \"{}\", status: {}): {}",
                    model, e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Etikettolkning mot Anthropics API misslyckades (modell: \"{}\")", model, e);
            return Optional.empty();
        }
    }

    private JsonNode callAnthropic(byte[] imageData, String mimeType) {
        String base64Image = Base64.getEncoder().encodeToString(imageData);
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image", "source", Map.of(
                                        "type", "base64", "media_type", mimeType, "data", base64Image)),
                                Map.of("type", "text", "text", PROMPT)
                        )
                ))
        );
        return restClient.post()
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
    }

    private static String extractResponseText(JsonNode response) {
        return AnthropicApiClient.extractResponseText(response);
    }

    /**
     * Modellen kan trots instruktionen råka bädda in JSON:en i löptext
     * eller markdown-kodstaket - extractJson plockar ut det yttersta
     * {}-blocket istället för att lita blint på att svaret är ren JSON.
     */
    private Optional<InterpretedLabel> parseInterpretedLabel(String text) {
        try {
            JsonNode json = objectMapper.readTree(extractJson(text));
            String name = nullableText(json, "name");
            String producer = nullableText(json, "producer");
            Integer vintage = json.hasNonNull("vintage") ? json.get("vintage").asInt() : null;
            String country = nullableText(json, "country");
            String region = nullableText(json, "region");
            if (name == null && producer == null && vintage == null && country == null && region == null) {
                // Precis som chattens "tomt svar utan undantag" (se
                // AnthropicWineChatAssistant) - ett giltigt JSON-svar med
                // fem null-fält ÄR ett legitimt utfall för en oläsbar
                // etikett, men loggas ändå tillfälligt eftersom det också
                // är precis vad ett trunkerat/tomt modellsvar ser ut som.
                log.warn("Etikettolkning mot Anthropics API gav inga tolkade fält (modell: \"{}\"), rå text: {}",
                        model, text);
                return Optional.empty();
            }
            return Optional.of(new InterpretedLabel(name, producer, vintage, country, region));
        } catch (Exception e) {
            // Nås t.ex. om text är tom/inte JSON alls - denna inre catch
            // körs INNAN det yttre interpret()-fångstblocket någonsin ser
            // felet, så utan denna rad var även den här grenen helt tyst.
            log.warn("Etikettolkning mot Anthropics API gav ett otolkbart svar (modell: \"{}\"), rå text: {}",
                    model, text, e);
            return Optional.empty();
        }
    }

    private static String nullableText(JsonNode json, String field) {
        return json.hasNonNull(field) ? json.get(field).asText() : null;
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }
}
