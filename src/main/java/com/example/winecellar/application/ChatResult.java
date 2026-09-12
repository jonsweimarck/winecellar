package com.example.winecellar.application;

import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.Conversation;

/**
 * Resultatet av att skicka ett meddelande (WINE-48) - antingen lyckades det
 * (assistenten svarade, eller {@code ChatService} satte in ett
 * standardmeddelande om den externa tjänsten misslyckades, se
 * {@code ChatService.ASSISTANT_UNAVAILABLE_MESSAGE}), eller så stoppades
 * det innan något meddelande ens sparades eftersom en gräns redan var nådd.
 */
public sealed interface ChatResult {

    record Success(Conversation conversation, ChatMessage assistantMessage) implements ChatResult {
    }

    record LimitReached(String message) implements ChatResult {
    }
}
