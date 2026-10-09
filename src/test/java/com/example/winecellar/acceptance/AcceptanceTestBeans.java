package com.example.winecellar.acceptance;

import com.example.winecellar.support.FakeMailSender;
import com.example.winecellar.support.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Testdubbletter för WINE-59-scenarierna i den delade Cucumber-kontexten:
 * en klocka som går att flytta framåt och en mail-fake som samlar utskicken.
 * Allt annat (databas, säkerhetskedja, tjänster) är på riktigt.
 */
@TestConfiguration
public class AcceptanceTestBeans {

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public FakeMailSender fakeMailSender() {
        return new FakeMailSender();
    }
}
