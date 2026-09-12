package com.example.winecellar.domain;

import java.time.Instant;

/**
 * En chattkonversation om användarens vinsamling (WINE-48, se docs/adr/
 * 0021-wine-chat-conversational-llm-integration.md). Tunt, som {@link Wine}
 * och {@link User} (ADR 0001) - meddelandena själva ({@link ChatMessage})
 * är en egen, separat entitet snarare än en inbäddad lista här, eftersom de
 * läggs till en och en och aldrig ändras i efterhand.
 *
 * {@code title} sätts en gång vid skapande (avkortat från det första
 * meddelandet, se {@code ChatService}) och ändras aldrig - ingen egen
 * "byt namn"-funktion i den här versionen.
 */
public record Conversation(
        ConversationId id,
        User.UserId owner,
        String title,
        Instant createdAt
) {

    public record ConversationId(Long value) {
    }
}
