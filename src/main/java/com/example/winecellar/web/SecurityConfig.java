package com.example.winecellar.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Hela appen kräver HTTP Basic-inloggning - till skillnad från roombooking
 * (som bara skyddade `/admin/**`) finns här inget legitimt anonymt
 * användningsfall: appen har ingen separat publik läsvy, så varje route
 * låter en besökare ändra vinsamlingen.
 *
 * Två roller: ADMIN (fullständig åtkomst) och READONLY (bara läsning -
 * kontot `readonly`/`readonly`, se README:s "Säkerhet"). READONLY nekas
 * inte bara POST/DELETE utan även GET-routerna för att lägga till/redigera
 * (`/wines/nytt`, `/wines/{id}/redigera`) - annars går det att gissa sig
 * till formulärsidan även om länkarna är dolda i UI:t (se vinkallare.html/
 * WineController, som döljer länkarna som ett extra lager, inte det enda).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/", "/wines/*/bild").hasAnyRole("ADMIN", "READONLY")
                        .requestMatchers(HttpMethod.GET, "/wines/nytt", "/wines/*/redigera").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/wines", "/wines/*/redigera", "/wines/*/dubblett-oka-antal", "/wines/tolka-etikett").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/wines/*").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // htmx-formulären skickar ingen CSRF-token, och autentiseringen är
                // stateless Basic-auth på varje anrop - inte en inloggad session
                // som CSRF-skyddet är till för.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${winecellar.admin.password}") String adminPassword) {
        var admin = User.withUsername("admin")
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();
        // Medvetet hårdkodat (inte en miljövariabel som admin-lösenordet) -
        // readonly/readonly är tänkt att vara ett känt, delbart konto för
        // att bara titta i samlingen, inte en hemlighet.
        var readonly = User.withUsername("readonly")
                .password(passwordEncoder.encode("readonly"))
                .roles("READONLY")
                .build();
        return new InMemoryUserDetailsManager(admin, readonly);
    }
}
