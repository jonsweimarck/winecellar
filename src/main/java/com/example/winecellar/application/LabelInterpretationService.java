package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Orkestrerar etikettolkningen (WINE-5) - en egen tjänst, inte en metod på
 * WineService, eftersom den inte rör den sparade vinsamlingen alls (ingen
 * WineRepository-åtkomst); den översätter bara ett foto till ett ännu
 * osparat utkast. Samma princip som ADR 0006 (orkestrering hör hemma i
 * applikationslagret, inte controllern), tillämpad på en annan gräns.
 */
@Service
public class LabelInterpretationService {

    private final LabelInterpreter labelInterpreter;

    public LabelInterpretationService(LabelInterpreter labelInterpreter) {
        this.labelInterpreter = labelInterpreter;
    }

    public LabelInterpretationResult interpret(byte[] imageData, String mimeType) {
        Optional<InterpretedLabel> interpreted = labelInterpreter.interpret(imageData, mimeType);
        if (interpreted.isEmpty()) {
            return new LabelInterpretationResult.Failed();
        }

        InterpretedLabel label = interpreted.get();
        Wine draft = Wine.builder()
                .name(label.name())
                .producer(label.producer())
                .vintage(label.vintage())
                .country(label.country())
                .region(label.region())
                .build();

        Set<String> interpretedFields = new HashSet<>();
        if (label.name() != null) {
            interpretedFields.add("name");
        }
        if (label.producer() != null) {
            interpretedFields.add("producer");
        }
        if (label.vintage() != null) {
            interpretedFields.add("vintage");
        }
        if (label.country() != null) {
            interpretedFields.add("country");
        }
        if (label.region() != null) {
            interpretedFields.add("region");
        }

        return new LabelInterpretationResult.Interpreted(draft, interpretedFields);
    }
}
