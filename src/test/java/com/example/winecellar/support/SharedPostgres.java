package com.example.winecellar.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * En enda Postgres-container för HELA testkörningen, delad av alla
 * IT-klasser som ärver den här basklassen.
 *
 * **Varför inte @Testcontainers/@Container:** de annotationerna knyter
 * containerns livscykel till EN testklass - en ny container startas och
 * rivs per klass. Ett statiskt startat fält lever i stället så länge
 * JVM:en gör, och Testcontainers egen upprensningsmekanism river
 * containern när körningen är slut.
 *
 * **Den större vinsten är inte containern utan Spring-kontexten.** Så
 * länge klasserna får IDENTISKA egenskapsvärden (samma JDBC-URL) blir
 * Springs cachningsnyckel för testkontexten densamma, och kontexten
 * återanvänds mellan klasserna i stället för att byggas om. Med en
 * container per klass var URL:en unik per klass, vilket gjorde
 * återanvändning omöjlig även när konfigurationen i övrigt var likadan.
 *
 * **Konsekvens att känna till:** databasen delas nu av alla klasser. Det
 * bär eftersom vinlistan är ägarscopead och varje klass använder sitt
 * eget testkonto - men en klass som rensar OSCOPEAT (t.ex. via
 * listWines(null), som returnerar samtliga viner) tömmer även andra
 * klassers rader. Det går bra så länge varje test sätter upp sina egna
 * data i @BeforeEach, vilket alla nuvarande klasser gör. Lägg inte till
 * en klass som förväntar sig att data överlever mellan testmetoder.
 */
public abstract class SharedPostgres {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    /**
     * Spring letar upp @DynamicPropertySource-metoder även i testklassens
     * arvskedja, så subklasserna behöver inte upprepa den här.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
