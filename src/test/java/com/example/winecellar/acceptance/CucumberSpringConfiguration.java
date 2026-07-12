package com.example.winecellar.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Delad Spring-kontext för alla Cucumber-scenarier. Persistensscenariot
 * (vin-persistens.feature) behöver en riktig Postgres - inte mockad, se
 * README - så vi startar en Testcontainers-container och pekar datasourcen
 * mot den. CRUD-scenarierna (lagga-till-vin.feature m.fl.) körs fortfarande
 * mot InMemoryWineRepository, instansierad direkt i respektive stegklass -
 * de bryr sig inte om denna Spring-kontext, men Cucumber kräver ändå exakt
 * en @CucumberContextConfiguration så fort cucumber-spring finns på classpath.
 */
@CucumberContextConfiguration
@SpringBootTest
@ContextConfiguration(initializers = CucumberSpringConfiguration.Initializer.class)
public class CucumberSpringConfiguration {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword()
            ).applyTo(context.getEnvironment());
        }
    }
}
