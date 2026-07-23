package com.example.winecellar.application;

import java.util.Optional;

/**
 * Port mot en LLM-driven tolkning av ett foto på en vinetikett - se WINE-5
 * och docs/adr/0012-label-scanning-llm-integration.md. Precis som
 * WineRepository har denna en riktig adapter (AnthropicLabelInterpreter,
 * infrastructure) och en testdubblett (FakeLabelInterpreter, bara i
 * testkoden - till skillnad från InMemoryWineRepository finns det ingen
 * legitim icke-test-användning av en låtsas-LLM).
 *
 * Optional.empty() betyder att tolkningen misslyckades helt (t.ex. nätverksfel
 * eller en etikett som inte gick att läsa något alls från) - inte att alla
 * fält råkade bli null i ett annars lyckat svar.
 */
public interface LabelInterpreter {

    Optional<InterpretedLabel> interpret(byte[] imageData, String mimeType);
}
