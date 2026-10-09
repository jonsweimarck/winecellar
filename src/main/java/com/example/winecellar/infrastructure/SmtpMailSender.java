package com.example.winecellar.infrastructure;

import com.example.winecellar.application.MailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SMTP-adapter (WINE-59, se ADR 0026). Skickar ASYNKRONT på en egen tråd:
 * dels för att en långsam SMTP-server inte ska hänga webbförfrågan, dels (för
 * "glömt lösenord") för att svarstiden inte ska avslöja om en adress finns -
 * ett riktigt utskick tar annars märkbart längre tid än "inget att skicka".
 * Fel loggas (utan mailets innehåll/token) och kastas aldrig vidare.
 */
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMailSender;
    private final String from;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mail-sender");
        thread.setDaemon(true);
        return thread;
    });

    public SmtpMailSender(JavaMailSender javaMailSender, String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        executor.submit(() -> {
            try {
                javaMailSender.send(message);
            } catch (RuntimeException e) {
                log.error("Kunde inte skicka mail \"{}\": {}", subject, e.toString());
            }
        });
    }
}
