package com.example.winecellar.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Valideringen är medvetet lös (WINE-59): korrekta adresser får inte underkännas. */
class EmailAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "anna@example.com",
            "anna+vin@example.com",
            "anna.lind@mail.example.co.uk",
            "a@b.se",
            "förnamn.efternamn@exempel.se",
            "anna@sub.domain.example.museum",
            "o'brien@example.ie",
            "anna_lind-2@example.photography",
            "1234567890@example.com"
    })
    void skaAccepteraGiltigaAdresser(String address) {
        assertThat(EmailAddress.normalize(address)).isPresent();
    }

    @Test
    void skaAccepteraPlustecken() {
        assertThat(EmailAddress.normalize("anna+vin@example.com")).contains("anna+vin@example.com");
    }

    @Test
    void skaTrimmaOchGöraGemener() {
        assertThat(EmailAddress.normalize("  Anna@Example.COM ")).contains("anna@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "inte-en-epost", "anna@", "@example.com", "anna@example", "anna@@example.com",
            "anna@example..com", "anna@.example.com", "anna@example.com.", "an na@example.com", "anna@exa mple.com"})
    void skaAvvisaUppenbartFelaktigaAdresser(String address) {
        assertThat(EmailAddress.normalize(address)).isEmpty();
    }

    @Test
    void skaAvvisaNullOchFörLångaAdresser() {
        assertThat(EmailAddress.normalize(null)).isEmpty();
        assertThat(EmailAddress.normalize("a".repeat(250) + "@example.com")).isEmpty();
    }
}
