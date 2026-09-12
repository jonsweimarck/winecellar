package com.example.winecellar.infrastructure;

import com.example.winecellar.application.WineChatAssistant;
import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Anropar samma externa tjänst och samma `RestClient`-mönster som
 * {@link AnthropicLabelInterpreter} (samma nyckel/modell, direkt REST-anrop
 * utan klientbibliotek) - men formad för en löpande konversation istället
 * för en enstaka strukturerad extraktion, se docs/adr/
 * 0021-wine-chat-conversational-llm-integration.md.
 */
@Component
public class AnthropicWineChatAssistant implements WineChatAssistant {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Du är en hjälpsam assistent för en vinsamlares privata vinkällare. Du svarar
            ALLTID på svenska. Du kan bara resonera kring den vinsamling som beskrivs
            nedan - du har ingen annan koppling till användarens data och kan aldrig
            ändra den. Om något inte går att svara på utifrån listan, säg det istället
            för att gissa.

            %s
            """;

    private static final Map<WineType, String> WINE_TYPE_LABELS = Map.of(
            WineType.RED, "rött",
            WineType.WHITE, "vitt",
            WineType.ROSE, "rosé",
            WineType.SPARKLING, "mousserande",
            WineType.FORTIFIED, "starkvin"
    );

    private final RestClient restClient;
    private final String model;

    public AnthropicWineChatAssistant(
            RestClient.Builder restClientBuilder,
            @Value("${winecellar.anthropic.api-key}") String apiKey,
            @Value("${winecellar.anthropic.model}") String model) {
        this.model = model;
        this.restClient = restClientBuilder
                .baseUrl("https://api.anthropic.com/v1/messages")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    @Override
    public Optional<String> reply(List<Wine> wines, List<ChatMessage> history) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 1024,
                    "system", SYSTEM_PROMPT_TEMPLATE.formatted(wineListAsText(wines)),
                    "messages", history.stream().map(AnthropicWineChatAssistant::toApiMessage).toList()
            );
            JsonNode response = restClient.post()
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
            String text = response.path("content").path(0).path("text").asText();
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Map<String, Object> toApiMessage(ChatMessage message) {
        String role = message.role() == ChatMessage.Role.USER ? "user" : "assistant";
        return Map.of("role", role, "content", message.content());
    }

    private static String wineListAsText(List<Wine> wines) {
        if (wines.isEmpty()) {
            return "Användarens vinkällare är för närvarande tom.";
        }
        String rows = wines.stream()
                .map(AnthropicWineChatAssistant::describeWine)
                .collect(Collectors.joining("\n"));
        return "Användarens vinkällare innehåller följande viner:\n" + rows;
    }

    /**
     * En rad fritext per vin, bara de fält som faktiskt är satta - samma
     * "hoppa över det som saknas" princip som resten av appens fältvisning
     * (t.ex. vinlistans detaljfält). Alla fält tas med, inte bara de som
     * visas direkt i vinlistan, eftersom assistenten ska kunna svara på
     * frågor om vad som helst i samlingen (se ADR 0021).
     */
    private static String describeWine(Wine wine) {
        return "- " + Stream.of(
                        wine.name(),
                        label("producent", wine.producer()),
                        label("årgång", wine.vintage()),
                        wine.wineType() == null ? null : WINE_TYPE_LABELS.get(wine.wineType()),
                        label("land", wine.country()),
                        label("region", wine.region()),
                        label("underregion", wine.subregion()),
                        label("druvor", wine.grapes()),
                        "antal flaskor: " + wine.quantity(),
                        label("plats", wine.location()),
                        label("inköpsdatum", wine.purchaseDate()),
                        label("pris", wine.price()),
                        label("varför köpt", wine.purchaseReason()),
                        label("tasting notes", wine.tastingNotes()),
                        wine.ownRating() == null ? null : label("eget betyg", wine.ownRating().label()),
                        wine.munskankarnaRating() == null ? null : label("Munskänkarnas betyg", wine.munskankarnaRating().label()),
                        label("Munskänkarnas bedömning", wine.munskankarnaReview()),
                        label("Vivino-betyg", wine.vivinoRating()),
                        label("Systembolagets produktnummer", wine.systembolagetProductNumber()),
                        label("Systembolagets beskrivning", wine.systembolagetDescription()),
                        label("annan referens", wine.otherReference())
                )
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private static String label(String label, Object value) {
        return value == null ? null : label + ": " + value;
    }
}
