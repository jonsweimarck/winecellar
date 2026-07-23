package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;

import java.util.Set;

/**
 * Resultatet av LabelInterpretationService.interpret(...) - se WINE-5.
 * `interpretedFields` är namnen (Wine-fältnamn: "name", "producer",
 * "vintage", "country", "region") på de fält som faktiskt kunde
 * läsas/härledas, använt av vin-formular.html för att markera dem
 * visuellt tills användaren rör fältet.
 */
public sealed interface LabelInterpretationResult {

    record Interpreted(Wine draft, Set<String> interpretedFields) implements LabelInterpretationResult {
    }

    record Failed() implements LabelInterpretationResult {
    }
}
