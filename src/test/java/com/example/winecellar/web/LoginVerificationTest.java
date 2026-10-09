package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inloggningssidan och att ett overifierat konto nekas (WINE-59) genom den riktiga säkerhetskedjan. */
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LoginVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    void stubKonton() {
        Instant now = Instant.now();
        when(userRepository.findByUsername("ny@example.com")).thenReturn(Optional.of(
                new User(new UserId(1L), "ny@example.com", passwordEncoder.encode("hemligt123"), now, 1, false,
                        false, now, false)));
        when(userRepository.findByUsername("gammal@example.com")).thenReturn(Optional.of(
                new User(new UserId(2L), "gammal@example.com", passwordEncoder.encode("hemligt123"), now, 1,
                        false, false, now, true)));
    }

    @Test
    void skaVisaLänkenGlömtDittLösenordOchMeddelandenPåInloggningssidan() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/glomt-losenord\"")))
                .andExpect(content().string(containsString("Glömt ditt lösenord?")));
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("Fel användarnamn eller lösenord")))
                .andExpect(content().string(containsString("/verifiera/ny")));
        mockMvc.perform(get("/login").param("registered", ""))
                .andExpect(content().string(containsString("verifieringslänk")));
    }

    @Test
    void skaNekaOverifieratKontoUtanAttAvslöjaAttDetÄrOverifierat() throws Exception {
        mockMvc.perform(post("/login").with(csrf()).param("username", "ny@example.com").param("password", "hemligt123"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void skaInteAvslöjaOverifieratKontoNärLösenordetÄrFel() throws Exception {
        mockMvc.perform(post("/login").with(csrf()).param("username", "ny@example.com").param("password", "fel"))
                .andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(post("/login").with(csrf()).param("username", "okand@example.com").param("password", "x"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void skaLoggaInVerifieratKonto() throws Exception {
        mockMvc.perform(post("/login").with(csrf()).param("username", "gammal@example.com")
                        .param("password", "hemligt123"))
                .andExpect(redirectedUrl("/"));
    }
}
