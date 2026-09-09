package com.example.winecellar.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enhetstest av den rena varningslogiken i {@link ProdProfileGuard}, utan
 * att boota någon Spring-kontext - se klassens Javadoc för varför den här
 * varningen finns.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProdProfileGuardTest {

    @Test
    void skaVarnaOmCleverCloudSignalÄrSattMenProdProfilenSaknas(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(ProdProfileGuard.CLEVER_CLOUD_SIGNAL_PROPERTY, "some-postgresql-host");

        new ProdProfileGuard(environment).warnIfNeeded();

        assertThat(output).contains("SPRING_PROFILES_ACTIVE=prod");
    }

    @Test
    void skaInteVarnaOmProdProfilenÄrAktiv(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(ProdProfileGuard.CLEVER_CLOUD_SIGNAL_PROPERTY, "some-postgresql-host");
        environment.setActiveProfiles(ProdProfileGuard.PROD_PROFILE);

        new ProdProfileGuard(environment).warnIfNeeded();

        assertThat(output).doesNotContain("SPRING_PROFILES_ACTIVE=prod");
    }

    @Test
    void skaInteVarnaLokaltUtanCleverCloudSignal(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();

        new ProdProfileGuard(environment).warnIfNeeded();

        assertThat(output).doesNotContain("SPRING_PROFILES_ACTIVE=prod");
    }
}
