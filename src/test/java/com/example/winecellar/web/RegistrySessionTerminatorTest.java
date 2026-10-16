package com.example.winecellar.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrySessionTerminatorTest {

    @Test
    void skaAvslutaEnbartDenAngivnaAnvändarensSessioner() {
        SessionRegistryImpl registry = new SessionRegistryImpl();
        var anna = User.withUsername("anna@example.com").password("x").authorities(List.of()).build();
        var bob = User.withUsername("bob@example.com").password("x").authorities(List.of()).build();
        registry.registerNewSession("s1", anna);
        registry.registerNewSession("s2", anna);
        registry.registerNewSession("s3", bob);

        new RegistrySessionTerminator(registry).terminateSessionsOf("Anna@Example.com");

        assertThat(registry.getSessionInformation("s1").isExpired()).isTrue();
        assertThat(registry.getSessionInformation("s2").isExpired()).isTrue();
        assertThat(registry.getSessionInformation("s3").isExpired()).isFalse();
    }
}
