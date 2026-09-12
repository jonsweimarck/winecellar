package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineChatAssistant;
import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.Wine;

import java.util.List;
import java.util.Optional;

/**
 * Testdubblett för WineChatAssistant (WINE-48) - samma roll som
 * FakeLabelInterpreter: ingen legitim produktionsanvändning av en
 * låtsas-LLM, så den hör hemma i testkoden, inte infrastructure/.
 */
final class FakeWineChatAssistant implements WineChatAssistant {

    private Optional<String> nextReply = Optional.empty();

    void willReply(String reply) {
        nextReply = Optional.of(reply);
    }

    @Override
    public Optional<String> reply(List<Wine> wines, List<ChatMessage> history) {
        return nextReply;
    }
}
