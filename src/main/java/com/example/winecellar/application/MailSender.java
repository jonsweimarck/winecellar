package com.example.winecellar.application;

/**
 * Port för utgående e-post (WINE-59, se ADR 0026). Adaptrar: SMTP (när
 * konfigurerat), en logg-adapter för lokal körning och en fake i tester.
 * Implementationer får aldrig kasta - ett misslyckat utskick ska inte fälla
 * registrering/återställning (användaren kan begära ett nytt mail), och ska
 * inte heller skilja sig i beteende mellan känd och okänd adress.
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
