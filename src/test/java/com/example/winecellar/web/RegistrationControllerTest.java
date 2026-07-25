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
import org.springframework.test.context.TestPropertySource;
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
 * Testar bara webblagret: RegistrationService är stubbad. Verifierar det
 * som RegistrationSteps (Cucumber, applikationslagret) inte täcker -
 * auto-inloggningen efter registrering (sessionen faktiskt satt) och
 * felrendering. Samma @TestPropertySource-pinning som WineControllerTest,
 * av samma skäl (se den klassens Javadoc).
 */
@WebMvcTest(RegistrationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "winecellar.admin.password=admin")
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
        @DisplayName("ska formuläret visas utan inloggning")
        void skaFormuläretVisasUtanInloggning() throws Exception {
            mockMvc.perform(get("/registrera"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"username\"")));
        }
    }

    @Nested
    @DisplayName("när ett konto registreras")
    class NärEttKontoRegistreras {

        @Test
        @DisplayName("ska ett lyckat konto logga in användaren direkt (sessionen satt) och omdirigera till startsidan")
        void skaLoggaInDirektOchOmdirigera() throws Exception {
            User user = new User(new UserId(1L), "vinälskare", "hashat", Instant.now());
            when(registrationService.register("vinälskare", "hemligt123"))
                    .thenReturn(new RegistrationResult.Registered(user));

            MvcResult result = mockMvc.perform(post("/registrera")
                            .with(csrf())
                            .param("username", "vinälskare")
                            .param("password", "hemligt123")
                            .param("confirmPassword", "hemligt123"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"))
                    .andReturn();

            assertThat(result.getRequest().getSession().getAttribute(SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
        }

        @Test
        @DisplayName("ska nekas om användarnamnet är upptaget, utan att omdirigera")
        void skaNekasOmAnvändarnamnetÄrUpptaget() throws Exception {
            when(registrationService.register("vinälskare", "hemligt123"))
                    .thenReturn(new RegistrationResult.UsernameTaken());

            mockMvc.perform(post("/registrera")
                            .with(csrf())
                            .param("username", "vinälskare")
                            .param("password", "hemligt123")
                            .param("confirmPassword", "hemligt123"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("upptaget")));
        }

        @Test
        @DisplayName("ska nekas om lösenorden inte matchar, utan att nå RegistrationService")
        void skaNekasOmLösenordenInteMatchar() throws Exception {
            mockMvc.perform(post("/registrera")
                            .with(csrf())
                            .param("username", "vinälskare")
                            .param("password", "hemligt123")
                            .param("confirmPassword", "annat"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("matchar inte")));

            verify(registrationService, never()).register(any(), any());
        }

        @Test
        @DisplayName("ska nekas om lösenordet saknas, utan att nå RegistrationService")
        void skaNekasOmLösenordetSaknas() throws Exception {
            mockMvc.perform(post("/registrera")
                            .with(csrf())
                            .param("username", "vinälskare")
                            .param("password", "")
                            .param("confirmPassword", ""))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Fyll i")));

            verify(registrationService, never()).register(any(), any());
        }
    }
}
