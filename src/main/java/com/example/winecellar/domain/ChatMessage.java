package com.example.winecellar.domain;

import java.time.Instant;

/**
 * Ett enskilt meddelande i en {@link Conversation} (WINE-48) - antingen
 * skrivet av användaren eller svarat av assistenten. Meddelanden ändras
 * aldrig i efterhand, bara läggs till.
 */
public record ChatMessage(
        ChatMessageId id,
        Conversation.ConversationId conversationId,
        Role role,
        String content,
        Instant createdAt
) {

    public enum Role {
        USER, ASSISTANT
    }

    public record ChatMessageId(Long value) {
    }
}
