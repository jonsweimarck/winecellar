package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationResult;
import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testar bara webblagret: RegistrationService är stubbad. Sedan WINE-59
 * loggas användaren INTE in efter registrering (kontot är overifierat) -
 * testet verifierar att ingen session sätts, plus felrendering och CSRF.
 */
@WebMvcTest(RegistrationController.class)
@Import(SecurityConfig.class)
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private UserRepository userRepository;

    @Nested
    @DisplayName("när registreringssidan visas")
    class NärRegistreringssidanVisas {

        @Test
        @DisplayName("ska formuläret visas utan inloggning och fråga efter en e-postadress")
        void skaFormuläretVisasUtanInloggning() throws Exception {
            mockMvc.perform(get("/registrera"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"username\"")))
                    .andExpect(content().string(containsString("E-postadress")));
        }
    }

    @Nested
    @DisplayName("när ett konto registreras")
    class NärEttKontoRegistreras {

        @Test
        @DisplayName("ska ett lyckat konto INTE logga in användaren utan omdirigera till inloggningen med besked")
        void skaInteLoggaInUtanOmdirigeraTillInloggningen() throws Exception {
            User user = new User(new UserId(1L), "anna@example.com", "hashat", Instant.now(), 1, false, false,
                    Instant.now(), false);
            when(registrationService.register("anna@example.com"))
                    .thenReturn(new RegistrationResult.Registered(user));

            MvcResult result = mockMvc.perform(post("/registrera")
                            .with(csrf())
                            .param("username", "anna@example.com"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?registered"))
                    .andReturn();

            assertThat(result.getRequest().getSession(false) == null
                    || result.getRequest().getSession().getAttribute(SPRING_SECURITY_CONTEXT_KEY) == null).isTrue();
        }

        @Test
        @DisplayName("ska nekas med besked om att användarnamnet måste vara en e-postadress")
        void skaNekasOmInteEpost() throws Exception {
            when(registrationService.register("inte-en-epost"))
                    .thenReturn(new RegistrationResult.InvalidEmail());

            mockMvc.perform(post("/registrera")
                            .with(csrf())
                            .param("username", "inte-en-epost"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("måste vara en e-postadress")));
        }

        @Test
        @DisplayName("ska nekas om användarnamnet är upptaget, utan att omdirigera")
        void skaNekasOmAnvändarnamnetÄrUpptaget() throws Exception {
            when(registrationService.register("anna@example.com"))
                    .thenReturn(new RegistrationResult.UsernameTaken());

            mockMvc.perform(post("/registrera")
                            .with(csrf())
                            .param("username", "anna@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("upptaget")));
        }

        @Test
        @DisplayName("ska nekas om e-postadressen saknas, utan att nå RegistrationService")
        void skaNekasOmAdressenSaknas() throws Exception {
            mockMvc.perform(post("/registrera").with(csrf()).param("username", ""))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Fyll i")));

            verify(registrationService, never()).register(any());
        }

        @Test
        @DisplayName("ska formuläret INTE fråga efter något lösenord")
        void skaInteFrågaEfterLösenord() throws Exception {
            mockMvc.perform(get("/registrera"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"password\""))));
        }

        @Test
        @DisplayName("ska avvisa en POST utan CSRF-token")
        void skaKrävaCsrf() throws Exception {
            mockMvc.perform(post("/registrera")
                            .param("username", "anna@example.com"))
                    .andExpect(status().isForbidden());

            verify(registrationService, never()).register(any());
        }
    }
}
