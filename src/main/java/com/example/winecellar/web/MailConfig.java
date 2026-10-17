package com.example.winecellar.web;

import com.example.winecellar.application.MailBudget;
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

    /** Budget för registrering och "ny länk" (WINE-65). Skild från återställningsbudgeten nedan. */
    @Bean
    public MailBudget registrationMailBudget(
            @Value("${winecellar.mail.max-per-hour:30}") int perHour,
            @Value("${winecellar.mail.max-per-day:150}") int perDay, Clock clock) {
        return new MailBudget(perHour, perDay, clock);
    }

    /** Egen, mindre budget för "glömt lösenord", så att ett fullt registreringstak aldrig svälter återställning. */
    @Bean
    public MailBudget resetMailBudget(
            @Value("${winecellar.mail.reset-max-per-hour:10}") int perHour,
            @Value("${winecellar.mail.reset-max-per-day:50}") int perDay, Clock clock) {
        return new MailBudget(perHour, perDay, clock);
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
        boolean auth = StringUtils.hasText(username);
        sender.getJavaMailProperties().putAll(javaMailProperties(startTls, auth));
        if (auth) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        return new SmtpMailSender(sender, from);
    }

    /**
     * JavaMail-egenskaper för SMTP. När STARTTLS är påslaget är det också OBLIGATORISKT
     * (WINE-65): utan starttls.required faller JavaMail tyst tillbaka på ren text om servern
     * inte erbjuder STARTTLS, och lösenordet och länkarna i mailen skickas då okrypterat.
     */
    static Properties javaMailProperties(boolean startTls, boolean auth) {
        Properties props = new Properties();
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        props.put("mail.smtp.starttls.required", String.valueOf(startTls));
        if (auth) {
            props.put("mail.smtp.auth", "true");
        }
        return props;
    }
}
