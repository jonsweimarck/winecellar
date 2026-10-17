package com.example.winecellar.web;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** WINE-65: STARTTLS är obligatoriskt när det är påslaget - JavaMail får inte falla tillbaka på ren text. */
class MailConfigTest {

    @Test
    void skaKrävaStarttlsNärDetÄrPåslaget() {
        Properties props = MailConfig.javaMailProperties(true, true);

        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    @Test
    void skaInteKrävaStarttlsNärDetÄrAvstängtOchInteSättaAuthUtanAnvändarnamn() {
        Properties props = MailConfig.javaMailProperties(false, false);

        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("false");
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("false");
        assertThat(props.getProperty("mail.smtp.auth")).isNull();
    }
}
