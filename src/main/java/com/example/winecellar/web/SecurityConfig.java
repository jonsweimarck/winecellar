package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Hela appen kräver inloggning - till skillnad från roombooking (som bara
 * skyddade `/admin/**`) finns här inget legitimt anonymt användningsfall:
 * appen har ingen separat publik läsvy, så varje route låter en besökare
 * ändra sin egen vinsamling.
 *
 * **Formulärbaserad inloggning med session, inte HTTP Basic (WINE-12, se
 * ADR 0013)** - CSRF är påslaget - `vinkallare.html`s htmx-formulär skickar
 * en CSRF-header via en liten `htmx:configRequest`-lyssnare, och
 * `thymeleaf-extras-springsecurity6` injicerar automatiskt CSRF-fältet i
 * varje `th:action`-formulär (login.html, registrera.html, vin-formular.html).
 *
 * **Inga roller längre (WINE-15, se ADR 0013).** De hårdkodade
 * `admin`/`readonly`-kontona (och `WINECELLAR_ADMIN_PASSWORD`) är borttagna -
 * `UserDetailsService` läser numera bara från `UserRepository`
 * (databasen, WINE-10/WINE-11). Alla inloggade användare har samma
 * rättigheter, bara till sin egen data (scopead sedan WINE-13) - det
 * fanns inget kvar att skilja ADMIN från READONLY på, så hela
 * roll-uppdelningen i `authorizeHttpRequests` togs bort samtidigt
 * (bara `authenticated()`). De ~30 vinerna som fanns innan `owner_id`
 * (WINE-10) migrerades till ett riktigt konto i WINE-17 innan det här
 * kunde göras säkert - annars hade admin-kontots oscopeade vy försvunnit
 * innan någon annan väg in till samma data fanns.
 *
 * **"Håll mig inloggad" (WINE-40, se ADR 0020) använder Spring Securitys
 * inbyggda, hash-baserade remember-me-läge, inte det databasbackade
 * persistenta läget.** Kort sagt är appens nuvarande skala - utan
 * krav på att kunna återkalla ett enskilt kvarglömt konto/enhet i
 * förväg - inte värd den extra tabellen/städlogiken det persistenta
 * läget kräver. Nyckeln som signerar cookien läses från konfiguration
 * (`winecellar.remember-me.key`) - en förutsägbar, commitad nyckel i
 * produktion hade låtit vem som helst med tillgång till en
 * lösenordshash (t.ex. efter ett databasläckage) förfalska en giltig
 * cookie för valfri användare.
 *
 * **Fail-safe fallback, inte en bekväm men osäker default (fixat i
 * kodgranskning, WINE-40).** Till skillnad från
 * `WINECELLAR_ANTHROPIC_API_KEY` (vars tomma default bara gör
 * etikettskanningen overksam) hade en tom/förutsägbar nyckel HÄR
 * fortfarande dugt för att signera en giltig cookie - "tom sträng" är
 * lika mycket ett känt, förfalskningsbart värde som en hårdkodad
 * sträng. Den lokala defaulten är därför tom, och remember-me-stödet
 * registreras inte alls i {@code HttpSecurity} om nyckeln saknas/är
 * blank - glöms miljövariabeln bort i produktion blir "håll mig
 * inloggad"-kryssrutan overksam (ingen cookie sätts, användaren loggas
 * bara ut som vanligt när sessionen tar slut) istället för att tyst
 * signera cookies med en offentligt känd nyckel.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * 30 dagar - samma storleksordning som de flesta webbplatsers "håll
     * mig inloggad". Ingen anledning att göra detta konfigurerbart per
     * miljö; till skillnad från nyckeln är giltighetstiden inte en
     * hemlighet.
     */
    private static final int REMEMBER_ME_VALIDITY_SECONDS = 60 * 60 * 24 * 30;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Value("${winecellar.remember-me.key}") String rememberMeKey) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        // Statiska resurser måste vara öppna: de behövs av
                        // login- och registreringssidan, som per definition
                        // renderas för en ANONYM besökare. Utan den här raden
                        // träffas de av anyRequest().authenticated() nedan och
                        // omdirigeras till /login - inloggningssidan hade då
                        // renderats helt ostylad (WINE-39).
                        .requestMatchers("/css/**", "/js/**").permitAll()
                        .requestMatchers("/registrera").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        if (StringUtils.hasText(rememberMeKey)) {
            http.rememberMe(rememberMe -> rememberMe
                    .key(rememberMeKey)
                    .tokenValiditySeconds(REMEMBER_ME_VALIDITY_SECONDS));
        } else {
            // Ingen nyckel konfigurerad - registrera INTE remember-me-stödet
            // alls, hellre än att falla tillbaka till ett känt/förutsägbart
            // värde som skulle duga lika bra för en förfalskad cookie. Utan
            // att .rememberMe(...) någonsin anropas lägger Spring Security
            // aldrig till filtret, så "håll mig inloggad"-kryssrutan blir
            // overksam istället för osäker.
            log.warn("winecellar.remember-me.key saknas/är tom - "
                    + "\"håll mig inloggad\" är avstängt tills en riktig nyckel konfigureras.");
        }
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByUsername(username)
                .map(user -> User.withUsername(user.username())
                        .password(user.hashedPassword())
                        .authorities(List.of())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
