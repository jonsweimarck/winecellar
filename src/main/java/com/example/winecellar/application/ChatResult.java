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

    /**
     * Assistenten misslyckades helt att svara (se {@code WineChatAssistant}s
     * "tomt = totalt misslyckande"-konvention). Till skillnad från ett tidigare
     * försök sätts INGET falskt {@code ChatMessage} in i konversationen här -
     * ett sådant meddelande hade skickats tillbaka till den externa tjänsten
     * som en äkta tidigare assistent-tur nästa gång användaren skriver något,
     * vilket kan få modellen att bygga vidare på ett svar den aldrig faktiskt
     * gav. Webblagret visar {@code conversation} med bara användarens eget
     * meddelande och ett separat felmeddelande, precis som för
     * {@link LimitReached}.
     */
    record AssistantUnavailable(Conversation conversation) implements ChatResult {
    }
}
