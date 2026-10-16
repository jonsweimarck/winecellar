package com.example.winecellar.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class MailConfigGuardTest {

    @Test
    void skaVarnaOmCleverCloudMenMailHostOchBaseUrlSaknas(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(RememberMeKeyGuard.CLEVER_CLOUD_SIGNAL_PROPERTY, "host");

        new MailConfigGuard(environment).warnIfNeeded();

        assertThat(output).contains("WINECELLAR_MAIL_HOST").contains("WINECELLAR_BASE_URL");
    }

    @Test
    void skaVarnaBaraFörDetSomSaknas(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(RememberMeKeyGuard.CLEVER_CLOUD_SIGNAL_PROPERTY, "host");
        environment.setProperty("WINECELLAR_MAIL_HOST", "smtp.example.com");
        environment.setProperty("WINECELLAR_BASE_URL", "  ");

        new MailConfigGuard(environment).warnIfNeeded();

        assertThat(output).doesNotContain("WINECELLAR_MAIL_HOST saknas").contains("WINECELLAR_BASE_URL saknas");
    }

    @Test
    void skaInteVarnaLokaltEllerNärAllaÄrSatta(CapturedOutput output) {
        new MailConfigGuard(new MockEnvironment()).warnIfNeeded();

        MockEnvironment configured = new MockEnvironment();
        configured.setProperty(RememberMeKeyGuard.CLEVER_CLOUD_SIGNAL_PROPERTY, "host");
        configured.setProperty("WINECELLAR_MAIL_HOST", "smtp.example.com");
        configured.setProperty("WINECELLAR_BASE_URL", "https://vin.example.com");
        new MailConfigGuard(configured).warnIfNeeded();

        assertThat(output).doesNotContain("saknas/är tom");
    }
}
