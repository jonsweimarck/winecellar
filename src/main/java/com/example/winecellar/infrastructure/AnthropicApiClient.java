package com.example.winecellar.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

/**
 * Delad uppkopplings-/svarstolkningslogik för Anthropics Messages API,
 * återanvänd av både {@link AnthropicLabelInterpreter} och
 * {@link AnthropicWineChatAssistant} - de pratar med samma tjänst på samma
 * sätt (bas-URL, autentiseringsheader, API-version, hur text plockas ut ur
 * svaret), bara med olika request-innehåll. Se docs/adr/
 * 0012-label-scanning-llm-integration.md och docs/adr/
 * 0021-wine-chat-conversational-llm-integration.md.
 */
final class AnthropicApiClient {

    private AnthropicApiClient() {
    }

    static RestClient build(RestClient.Builder restClientBuilder, String apiKey) {
        return restClientBuilder
                .baseUrl("https://api.anthropic.com/v1/messages")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    /**
     * Letar upp det FÖRSTA innehållsblocket av typen "text" i svaret, inte
     * bara content[0] - claude-sonnet-5 visade sig (produktionsfelsökning,
     * 2026-09) lägga ett "thinking"-block FÖRE textblocket i arrayen även
     * utan att resonemang efterfrågats i requesten, vilket gjorde att
     * content[0]-antagandet plockade ett tomt resonemangsblock istället för
     * det faktiska svaret.
     */
    static String extractResponseText(JsonNode response) {
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText();
            }
        }
        return "";
    }
}
