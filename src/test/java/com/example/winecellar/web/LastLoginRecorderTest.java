package com.example.winecellar.web;

import com.example.winecellar.support.TestUsers;
import com.example.winecellar.application.AdminService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Senaste login (WINE-62) genom den riktiga säkerhetskedjan: bara en lyckad
 * formulärinloggning eller remember-me-återinloggning uppdaterar tidpunkten,
 * en misslyckad ändrar inget, och ett fel i uppdateringen fäller aldrig
 * inloggningen. (Händelselyssnaren är en vanlig @Component och laddas inte av
 * @WebMvcTest av sig själv - därav @Import.)
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, LastLoginRecorder.class})
@TestPropertySource(properties = "winecellar.remember-me.key=test-remember-me-nyckel")
class LastLoginRecorderTest {

    private static final UserId BOB_ID = new UserId(2L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AdminService adminService;

    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    void stubBob() {
        Instant old = Instant.parse("2020-01-01T10:00:00Z");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(
                TestUsers.verifiedUser(BOB_ID, "bob", passwordEncoder.encode("hemligt123"), old, 1, false, true, old)));
    }

    @Test
    void skaUppdateraSenasteLoginVidLyckadFormulärinloggning() throws Exception {
        Instant before = Instant.now();

        mockMvc.perform(post("/login").with(csrf()).param("username", "bob").param("password", "hemligt123"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).updateLastLogin(org.mockito.ArgumentMatchers.eq(BOB_ID), at.capture());
        assertThat(at.getValue()).isBetween(before, Instant.now());
    }

    @Test
    void skaInteÄndraSenasteLoginVidMisslyckadInloggning() throws Exception {
        mockMvc.perform(post("/login").with(csrf()).param("username", "bob").param("password", "fel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        verify(userRepository, never()).updateLastLogin(any(), any());
    }

    @Test
    void skaUppdateraSenasteLoginNärRememberMeCookienLoggarInIgen() throws Exception {
        MvcResult inloggning = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "bob").param("password", "hemligt123").param("remember-me", "on"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie rememberMe = inloggning.getResponse().getCookie("remember-me");
        assertThat(rememberMe).isNotNull();
        verify(userRepository, times(1)).updateLastLogin(any(), any());

        // Ingen session, bara cookien: remember-me-filtret loggar in användaren på nytt.
        mockMvc.perform(get("/admin").cookie(rememberMe)).andExpect(status().isOk());

        verify(userRepository, times(2)).updateLastLogin(any(), any());
    }

    @Test
    void skaLåtaInloggningenGåIgenomÄvenOmUppdateringenFelar() throws Exception {
        doThrow(new IllegalStateException("databasen nere")).when(userRepository).updateLastLogin(any(), any());

        mockMvc.perform(post("/login").with(csrf()).param("username", "bob").param("password", "hemligt123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
