package com.example.winecellar.application;

import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.Wine;

import java.util.List;
import java.util.Optional;

/**
 * Port mot en LLM-driven konversation om användarens vinsamling (WINE-48) -
 * se docs/adr/0021-wine-chat-conversational-llm-integration.md för varför
 * det här är en EGEN port, skild från {@link LabelInterpreter}, trots att
 * båda pratar med samma externa tjänst: formen är fundamentalt olik (en
 * löpande konversation som skickar med hela sin egen historik, inte en
 * enstaka strukturerad extraktion).
 *
 * {@code wines} är HELA den inloggade användarens vinsamling, hämtad färsk
 * vid varje anrop - inte bara det som råkar synas i en filtrerad vy.
 * {@code history} är hela konversationen hittills, inklusive det senast
 * tillagda användarmeddelandet - den externa tjänsten är stateless mellan
 * anrop.
 *
 * {@code Optional.empty()} betyder att anropet misslyckades helt (nätverksfel,
 * fel från tjänsten) - samma konvention som {@link LabelInterpreter}.
 */
public interface WineChatAssistant {

    Optional<String> reply(List<Wine> wines, List<ChatMessage> history);
}
