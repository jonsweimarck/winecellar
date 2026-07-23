package com.example.winecellar.acceptance;

import com.example.winecellar.application.InterpretedLabel;
import com.example.winecellar.application.LabelInterpreter;

import java.util.Optional;

/**
 * Testdubblett för LabelInterpreter (WINE-5) - till skillnad från
 * InMemoryWineRepository finns det ingen legitim produktionsanvändning av
 * en låtsas-LLM, så den här hör hemma i testkoden, inte infrastructure/.
 */
final class FakeLabelInterpreter implements LabelInterpreter {

    private Optional<InterpretedLabel> nextResult = Optional.empty();

    void willReturn(InterpretedLabel label) {
        nextResult = Optional.of(label);
    }

    void willFail() {
        nextResult = Optional.empty();
    }

    @Override
    public Optional<InterpretedLabel> interpret(byte[] imageData, String mimeType) {
        return nextResult;
    }
}
