package com.example.winecellar.infrastructure;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** WINE-65: ett misslyckat (asynkront) utskick anropar misslyckandehanteraren så att en kvotplats kan återbetalas. */
class SmtpMailSenderTest {

    /** Minimal JavaMailSender som antingen lyckas eller kastar. */
    private static class StubJavaMailSender implements JavaMailSender {
        private final boolean failing;
        final AtomicInteger sent = new AtomicInteger();

        StubJavaMailSender(boolean failing) {
            this.failing = failing;
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            if (failing) {
                throw new MailSendException("smtp nere");
            }
            sent.incrementAndGet();
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MimeMessage createMimeMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void skaAnropaMisslyckandehanterarenNärUtskicketFelar() throws Exception {
        SmtpMailSender sender = new SmtpMailSender(new StubJavaMailSender(true), "no-reply@example.com");
        CountDownLatch failed = new CountDownLatch(1);

        sender.send("anna@example.com", "ämne", "text", failed::countDown);

        assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void skaInteAnropaMisslyckandehanterarenNärUtskicketLyckas() throws Exception {
        StubJavaMailSender stub = new StubJavaMailSender(false);
        SmtpMailSender sender = new SmtpMailSender(stub, "no-reply@example.com");
        AtomicInteger failures = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        sender.send("anna@example.com", "ämne", "text", failures::incrementAndGet);
        sender.send("anna@example.com", "ämne", "text", done::countDown); // körs efter det första (en tråd)
        // Båda lyckas -> ingen av hanterarna körs. Vänta tills båda utskicken gjorts.
        long deadline = System.currentTimeMillis() + 5000;
        while (stub.sent.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertThat(stub.sent.get()).isEqualTo(2);
        assertThat(failures.get()).isZero();
        assertThat(done.getCount()).isEqualTo(1);
    }

    @Test
    void skaAnropaMisslyckandehanterarenNärKönÄrFull() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        JavaMailSender blocking = new StubJavaMailSender(false) {
            @Override
            public void send(SimpleMailMessage simpleMessage) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        SmtpMailSender sender = new SmtpMailSender(blocking, "no-reply@example.com");
        AtomicInteger rejected = new AtomicInteger();

        // En tråd upptagen + kö på 100: de sista skickas ut ur kön
        for (int i = 0; i < 110; i++) {
            sender.send("anna@example.com", "ämne", "text", rejected::incrementAndGet);
        }
        release.countDown();

        assertThat(rejected.get()).isGreaterThanOrEqualTo(1);
    }
}
