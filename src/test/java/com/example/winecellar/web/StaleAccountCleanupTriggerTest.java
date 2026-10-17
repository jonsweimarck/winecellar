package com.example.winecellar.web;

import com.example.winecellar.application.StaleAccountCleanup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** WINE-65: städningen körs vid inloggning och får aldrig fälla den (komponenten laddas inte av @WebMvcTest). */
@ExtendWith(OutputCaptureExtension.class)
class StaleAccountCleanupTriggerTest {

    private final StaleAccountCleanup cleanup = mock(StaleAccountCleanup.class);
    private final StaleAccountCleanupTrigger trigger = new StaleAccountCleanupTrigger(cleanup);
    private final InteractiveAuthenticationSuccessEvent event = new InteractiveAuthenticationSuccessEvent(
            UsernamePasswordAuthenticationToken.authenticated("anna@example.com", "x", java.util.List.of()),
            StaleAccountCleanupTriggerTest.class);

    @Test
    void skaStädaVidLyckadInloggning() {
        trigger.onLogin(event);

        verify(cleanup).cleanup();
    }

    @Test
    void skaFångaOchLoggaFelUtanAttFällaInloggningenOchUtanAttLoggaFelmeddelandet(CapturedOutput output) {
        doThrow(new IllegalStateException("token_hash=hemlig-tokendata")).when(cleanup).cleanup();

        assertThatCode(() -> trigger.onLogin(event)).doesNotThrowAnyException();

        assertThat(output).contains("IllegalStateException").doesNotContain("hemlig-tokendata");
    }
}
