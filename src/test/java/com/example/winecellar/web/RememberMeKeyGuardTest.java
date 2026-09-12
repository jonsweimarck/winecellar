package com.example.winecellar.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enhetstest av den rena varningslogiken i {@link RememberMeKeyGuard}, utan
 * att boota någon Spring-kontext - se klassens Javadoc för varför den här
 * varningen finns (WINE-47). Samma mönster som {@link ProdProfileGuardTest}.
 */
@ExtendWith(OutputCaptureExtension.class)
class RememberMeKeyGuardTest {

    @Test
    void skaVarnaOmCleverCloudSignalÄrSattMenRememberMeNyckelnSaknas(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(RememberMeKeyGuard.CLEVER_CLOUD_SIGNAL_PROPERTY, "some-postgresql-host");

        new RememberMeKeyGuard(environment).warnIfNeeded();

        assertThat(output).contains("WINECELLAR_REMEMBER_ME_KEY");
    }

    @Test
    void skaVarnaOmCleverCloudSignalÄrSattMenRememberMeNyckelnÄrBlank(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(RememberMeKeyGuard.CLEVER_CLOUD_SIGNAL_PROPERTY, "some-postgresql-host");
        environment.setProperty(RememberMeKeyGuard.REMEMBER_ME_KEY_PROPERTY, "   ");

        new RememberMeKeyGuard(environment).warnIfNeeded();

        assertThat(output).contains("WINECELLAR_REMEMBER_ME_KEY");
    }

    @Test
    void skaInteVarnaOmRememberMeNyckelnÄrSatt(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(RememberMeKeyGuard.CLEVER_CLOUD_SIGNAL_PROPERTY, "some-postgresql-host");
        environment.setProperty(RememberMeKeyGuard.REMEMBER_ME_KEY_PROPERTY, "en-riktig-hemlighet");

        new RememberMeKeyGuard(environment).warnIfNeeded();

        assertThat(output).doesNotContain("WINECELLAR_REMEMBER_ME_KEY");
    }

    @Test
    void skaInteVarnaLokaltUtanCleverCloudSignal(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();

        new RememberMeKeyGuard(environment).warnIfNeeded();

        assertThat(output).doesNotContain("WINECELLAR_REMEMBER_ME_KEY");
    }
}
