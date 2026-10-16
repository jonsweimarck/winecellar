package com.example.winecellar.web;

import com.example.winecellar.application.MailSender;
import com.example.winecellar.infrastructure.LoggingMailSender;
import com.example.winecellar.infrastructure.SmtpMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.Properties;

/**
 * Mailkonfiguration (WINE-59, se ADR 0026). SMTP väljs bara när
 * {@code winecellar.mail.host} (WINECELLAR_MAIL_HOST) är satt; annars används
 * en logg-adapter som inte skickar något. Appen startar alltså alltid, även
 * utan SMTP-konfiguration. Boots egen mail-autokonfiguration (spring.mail.*)
 * används medvetet inte: en tom host-variabel skulle ändå aktivera den.
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /** En injicerbar klocka så att tidsberoende regler (24 h/1 h) går att testa. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public MailSender mailSender(
            @Value("${winecellar.mail.host:}") String host,
            @Value("${winecellar.mail.port:587}") int port,
            @Value("${winecellar.mail.username:}") String username,
            @Value("${winecellar.mail.password:}") String password,
            @Value("${winecellar.mail.from:no-reply@localhost}") String from,
            @Value("${winecellar.mail.starttls:true}") boolean startTls,
            @Value("${winecellar.mail.log-content:false}") boolean logContent) {
        if (!StringUtils.hasText(host)) {
            log.warn("winecellar.mail.host saknas - mail (verifiering, glömt lösenord) skickas INTE. "
                    + "Sätt WINECELLAR_MAIL_HOST m.fl. i produktion.");
            return new LoggingMailSender(logContent);
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        if (StringUtils.hasText(username)) {
            sender.setUsername(username);
            sender.setPassword(password);
            props.put("mail.smtp.auth", "true");
        }
        return new SmtpMailSender(sender, from);
    }
}
