package com.example.winecellar.web;

import com.example.winecellar.application.AdminService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webblagret för adminsidan (WINE-61): faktiskt renderad HTML, server-side
 * behörighet (inte bara dolda knappar), POST + redirect med flash-toast, och
 * att en raderad användares session/remember-me slutar fungera.
 */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "winecellar.remember-me.key=test-remember-me-nyckel")
class AdminControllerTest {

    private static final UserId ALICE_ID = new UserId(1L);
    private static final UserId BOB_ID = new UserId(2L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AdminService adminService;

    @MockBean
    private UserRepository userRepository;

    private User alice(boolean admin) {
        return new User(ALICE_ID, "alice", passwordEncoder.encode("hemligt123"), Instant.now(), 1, false, admin);
    }

    private User bob(boolean admin) {
        return new User(BOB_ID, "bob", "hash", Instant.now(), 1, false, admin);
    }

    @Test
    void skaListaAllaAnvändareMedRätträttKnappar() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice(true)));
        when(adminService.listUsers(ALICE_ID)).thenReturn(List.of(alice(true), bob(false)));

        mockMvc.perform(get("/admin").with(user("alice").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">alice<")))
                .andExpect(content().string(containsString(">bob<")))
                // Bob är inte admin -> "Gör till admin" (exakt en: alice är redan admin)
                .andExpect(content().string(containsString("Gör till admin")))
                // Radera finns för bob (data-user-id=2) men inte för den inloggade själv (1)
                .andExpect(content().string(containsString("data-user-id=\"2\"")))
                .andExpect(content().string(not(containsString("data-user-id=\"1\""))));
    }

    @Test
    void skaBekräftaRadering_iNativeDialog_inteWindowConfirm() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice(true)));
        when(adminService.listUsers(ALICE_ID)).thenReturn(List.of(alice(true), bob(false)));

        mockMvc.perform(get("/admin").with(user("alice").roles("ADMIN")))
                .andExpect(content().string(containsString("<dialog id=\"radera-dialog\"")))
                .andExpect(content().string(containsString("action=\"/admin/radera\"")))
                .andExpect(content().string(containsString("showModal()")))
                .andExpect(content().string(not(containsString("onsubmit"))));
    }

    @Test
    void skaNekaVanligAnvändareMed403PåSidanOchPostarna() throws Exception {
        mockMvc.perform(get("/admin").with(user("bob")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/gor-till-admin").param("userId", "2").with(user("bob")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/radera").param("userId", "2").with(user("bob")).with(csrf()))
                .andExpect(status().isForbidden());

        verify(adminService, never()).makeAdmin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(adminService, never()).deleteUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skaOmdirigeraUtloggadTillInloggning() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post("/admin/radera").param("userId", "2").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void skaRaderaViaPostOchRedirectaMedToast() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice(true)));
        when(userRepository.findById(BOB_ID)).thenReturn(Optional.of(bob(false)));
        when(adminService.deleteUser(ALICE_ID, BOB_ID)).thenReturn(true);

        mockMvc.perform(post("/admin/radera").param("userId", "2").with(user("alice").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("feedback", "Användaren bob raderades"));

        verify(adminService).deleteUser(ALICE_ID, BOB_ID);
    }

    @Test
    void skaGöraTillAdminViaPostOchRedirectaMedToast() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice(true)));
        when(userRepository.findById(BOB_ID)).thenReturn(Optional.of(bob(false)));
        when(adminService.makeAdmin(ALICE_ID, BOB_ID)).thenReturn(true);

        mockMvc.perform(post("/admin/gor-till-admin").param("userId", "2")
                        .with(user("alice").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("feedback"));
    }

    @Test
    void skaIgnoreraFörsökAttRaderaSigSjälvUtanToast() throws Exception {
        // AdminService (som spärrar självradering) returnerar false -> ingen toast, ingen felsida.
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice(true)));
        when(userRepository.findById(ALICE_ID)).thenReturn(Optional.of(alice(true)));
        when(adminService.deleteUser(ALICE_ID, ALICE_ID)).thenReturn(false);

        mockMvc.perform(post("/admin/radera").param("userId", "1").with(user("alice").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeCount(0));
    }

    @Test
    void skaSlutaFungeraForEnRaderadAnvändaresPågåendeSession() throws Exception {
        // bob (admin, för att kunna nå /admin) loggar in på riktigt och har en session.
        User bobAdmin = new User(BOB_ID, "bob", passwordEncoder.encode("hemligt123"), Instant.now(), 1, false, true);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bobAdmin));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice(true)));
        when(userRepository.findById(BOB_ID)).thenReturn(Optional.of(bobAdmin));
        when(adminService.deleteUser(ALICE_ID, BOB_ID)).thenReturn(true);

        MvcResult inloggning = mockMvc.perform(formLogin().user("bob").password("hemligt123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession bobsSession = (MockHttpSession) inloggning.getRequest().getSession(false);
        mockMvc.perform(get("/admin").session(bobsSession)).andExpect(status().isOk());

        mockMvc.perform(post("/admin/radera").param("userId", "2").with(user("alice").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin"));

        mockMvc.perform(get("/admin").session(bobsSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void skaSlutaFungeraForEnRaderadAnvändaresRememberMeCookie() throws Exception {
        User bobAdmin = new User(BOB_ID, "bob", passwordEncoder.encode("hemligt123"), Instant.now(), 1, false, true);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bobAdmin));

        MvcResult inloggning = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "bob").param("password", "hemligt123").param("remember-me", "on"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie rememberMe = inloggning.getResponse().getCookie("remember-me");
        assertThat(rememberMe).isNotNull();

        // Kontroll: innan raderingen räcker cookien ensam (ingen session) för att nå /admin.
        mockMvc.perform(get("/admin").cookie(rememberMe)).andExpect(status().isOk());

        // Användaren raderas -> UserDetailsService hittar hen inte längre.
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin").cookie(rememberMe))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
