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

    static String extractResponseText(JsonNode response) {
        return response.path("content").path(0).path("text").asText();
    }
}
