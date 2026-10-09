package com.example.winecellar.support;

import com.example.winecellar.application.MailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Testfake för mail-porten (WINE-59): samlar skickade mail i minnet. */
public class FakeMailSender implements MailSender {

    public record Mail(String to, String subject, String body) {

        /** Det råa tokenet ur länken i mailet. */
        public String token() {
            Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body);
            if (!matcher.find()) {
                throw new AssertionError("Ingen tokenlänk i mailet: " + body);
            }
            return matcher.group(1);
        }
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Mail(to, subject, body));
    }

    public List<Mail> sentTo(String address) {
        return sent.stream().filter(mail -> mail.to().equalsIgnoreCase(address)).toList();
    }

    public List<Mail> all() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}
