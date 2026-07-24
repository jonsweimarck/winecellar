package com.example.winecellar.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Hela appen kräver inloggning - till skillnad från roombooking (som bara
 * skyddade `/admin/**`) finns här inget legitimt anonymt användningsfall:
 * appen har ingen separat publik läsvy, så varje route låter en besökare
 * ändra vinsamlingen.
 *
 * **Formulärbaserad inloggning med session, inte HTTP Basic (WINE-12, se
 * ADR 0013)** - ersätter den tidigare `.httpBasic(...)`-mekanismen. CSRF är
 * därför påslaget igen (var avstängt när autentiseringen var stateless
 * Basic-auth per anrop) - `vinkallare.html`s htmx-formulär skickar en
 * CSRF-header via en liten `htmx:configRequest`-lyssnare, och
 * `thymeleaf-extras-springsecurity6` injicerar automatiskt CSRF-fältet i
 * varje `th:action`-formulär (login.html, vin-formular.html).
 *
 * **`UserDetailsService` är fortfarande den gamla hårdkodade
 * `InMemoryUserDetailsManager`-varianten (admin/readonly), INTE ännu
 * databasbackad via den nya `UserRepository`en (WINE-10).** Medveten
 * avvikelse från WINE-12s ursprungliga story-text: att redan här byta till
 * ett databasbackat `UserDetailsService` hade gjort att admin/readonly
 * slutade fungera direkt (ingen rad i `users`-tabellen ännu, eftersom
 * registrering inte finns förrän WINE-11) - både lokalt och i PRODUKTION,
 * där det riktiga admin-kontot faktiskt används. Databasbytet hör hemma i
 * WINE-11, som är den story som faktiskt skapar användare att logga in
 * som. Två roller lever kvar oförändrade tills WINE-15: ADMIN
 * (fullständig åtkomst) och READONLY (bara läsning - kontot
 * `readonly`/`readonly`, se README:s "Säkerhet"). READONLY nekas inte
 * bara POST/DELETE utan även GET-routerna för att lägga till/redigera
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
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
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
