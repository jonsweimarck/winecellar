package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
 * **`UserDetailsService` slår ihop två källor (WINE-11, se ADR 0013).**
 * De gamla hårdkodade `admin`/`readonly`-kontona (`InMemoryUserDetailsManager`,
 * kollas först) och nyregistrerade användare (`UserRepository`en från
 * WINE-10, kollas bara om användarnamnet inte matchar någon av de
 * hårdkodade). Medvetet inte helt ersatt av databasen än - admin/readonly
 * måste fortsätta fungera fram till WINE-15 (som tar bort dem, medvetet
 * sist av säkerhetsskäl, se CLAUDE.md), annars låser man ute produktionens
 * riktiga admin-konto innan det finns något annat sätt in. Två roller
 * lever kvar oförändrade tills WINE-15: ADMIN (fullständig åtkomst) och
 * READONLY (bara läsning - kontot `readonly`/`readonly`, se README:s
 * "Säkerhet"). READONLY nekas inte bara POST/DELETE utan även GET-routerna
 * för att lägga till/redigera (`/wines/nytt`, `/wines/{id}/redigera`) -
 * annars går det att gissa sig till formulärsidan även om länkarna är
 * dolda i UI:t (se vinkallare.html/WineController, som döljer länkarna
 * som ett extra lager, inte det enda). **Nyregistrerade användare får
 * ROLE_ADMIN** (satt i `RegistrationController`, inte här) - en medveten,
 * temporär förenkling: riktig scoping till den egna listan kommer först i
 * WINE-13, så alla inloggade användare delar i praktiken samma vinlista
 * fram tills dess.
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
                        .requestMatchers("/registrera").permitAll()
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
            UserRepository userRepository,
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
        UserDetailsService legacyAccounts = new InMemoryUserDetailsManager(admin, readonly);

        return username -> {
            try {
                return legacyAccounts.loadUserByUsername(username);
            } catch (UsernameNotFoundException legacyMiss) {
                return userRepository.findByUsername(username)
                        .map(user -> User.withUsername(user.username())
                                .password(user.hashedPassword())
                                .roles("ADMIN")
                                .build())
                        .orElseThrow(() -> new UsernameNotFoundException(username));
            }
        };
    }
}
