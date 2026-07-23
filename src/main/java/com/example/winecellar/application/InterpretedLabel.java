package com.example.winecellar.application;

/**
 * Vad LabelInterpreter kunde läsa/härleda ur en etikettbild - se WINE-5.
 * `name`/`producer`/`vintage` får aldrig gissas eller härledas av
 * adaptern, bara `country`/`region` (se AnthropicLabelInterpreters prompt).
 * Ett null-fält betyder "kunde inte läsas eller härledas", inte
 * "misslyckades helt" - se LabelInterpreter.interpret(...) för den
 * skillnaden.
 */
public record InterpretedLabel(String name, String producer, Integer vintage, String country, String region) {
}
