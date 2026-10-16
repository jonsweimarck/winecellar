package com.example.winecellar.infrastructure;

import com.example.winecellar.application.MailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mailadapter för lokal körning när ingen SMTP-server är konfigurerad
 * (WINE-59, se ADR 0026). Skickar ingenting. Mailets INNEHÅLL (som innehåller
 * en verifierings-/återställningslänk med ett giltigt token) loggas BARA om
 * {@code winecellar.mail.log-content=true} (WINECELLAR_MAIL_LOG_CONTENT) -
 * default av, så att ett glömt SMTP-bortval i produktion aldrig skriver
 * tokens i klartext i loggarna. Sätt variabeln lokalt för att kunna klicka
 * på länken.
 */
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    private final boolean logContent;

    public LoggingMailSender(boolean logContent) {
        this.logContent = logContent;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (logContent) {
            log.info("[mail, ej skickat - ingen SMTP konfigurerad] Till: {}\nÄmne: {}\n\n{}", to, subject, body);
        } else {
            log.warn("Mail \"{}\" skulle ha skickats, men ingen SMTP-server är konfigurerad (WINECELLAR_MAIL_HOST). "
                    + "Sätt WINECELLAR_MAIL_LOG_CONTENT=true lokalt för att se innehållet.", subject);
        }
    }
}
