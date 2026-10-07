package com.example.winecellar.web;

import com.example.winecellar.application.AdminService;
import com.example.winecellar.application.LabelInterpretationService;
import com.example.winecellar.application.RegistrationResult;
import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WINE-61: CurrentUser är fail-closed. En autentiserad principal vars användare
 * saknas i databasen (raderad medan sessionen lever) får ALDRIG tolkas som
 * "ingen ägare" - hen skickas till inloggningen, och
 * varken vinlistan eller exporten läser någon data.
 */
@WebMvcTest({WineController.class, ExportController.class, SettingsController.class,
        AdminController.class, RegistrationController.class})
@Import(SecurityConfig.class)
class DeletedUserAccessTest {

    private static final UserId GHOST_ID = new UserId(7L);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WineService wineService;

    @MockBean
    private LabelInterpretationService labelInterpretationService;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private AdminService adminService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void skaNekaVinlistaOchExportFörPrincipalVarsAnvändareSaknas() throws Exception {
        when(userRepository.findByUsername("spöke")).thenReturn(Optional.empty());

        mockMvc.perform(get("/").with(user("spöke")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
        mockMvc.perform(get("/export/xlsx").with(user("spöke")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
        mockMvc.perform(get("/export/bilder.zip").with(user("spöke")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));

        verify(wineService, never()).search(any(), any());
        verify(wineService, never()).listWines(any());
    }

    @Test
    void skaNekaEnNyregistreradAnvändaresSessionNärKontotRaderats() throws Exception {
        User registered = new User(GHOST_ID, "nyss", "hash", Instant.now(), 1, false, false);
        when(registrationService.register("nyss", "hemligt123"))
                .thenReturn(new RegistrationResult.Registered(registered));
        when(userRepository.findByUsername("nyss")).thenReturn(Optional.of(registered));

        MvcResult registrering = mockMvc.perform(post("/registrera").with(csrf())
                        .param("username", "nyss").param("password", "hemligt123")
                        .param("confirmPassword", "hemligt123"))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) registrering.getRequest().getSession(false);

        mockMvc.perform(get("/export/xlsx").session(session)).andExpect(status().isOk());

        // Kontot raderas av en admin (användaren finns inte längre i databasen)
        when(userRepository.findByUsername("nyss")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("chef")).thenReturn(Optional.of(
                new User(new UserId(1L), "chef", "hash", Instant.now(), 1, false, true)));
        when(userRepository.findById(GHOST_ID)).thenReturn(Optional.of(registered));
        when(adminService.deleteUser(new UserId(1L), GHOST_ID)).thenReturn(true);
        mockMvc.perform(post("/admin/radera").param("userId", "7").with(user("chef").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin"));

        // Både via SessionRegistry (upphävd session) och fail-closed CurrentUser nekas hen.
        mockMvc.perform(get("/export/xlsx").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void skaNekaEnNyregistreradAnvändaresSessionNärKontotSaknasÄvenUtanUpphävdSession() throws Exception {
        User registered = new User(GHOST_ID, "nyss", "hash", Instant.now(), 1, false, false);
        when(registrationService.register("nyss", "hemligt123"))
                .thenReturn(new RegistrationResult.Registered(registered));
        when(userRepository.findByUsername("nyss")).thenReturn(Optional.empty());

        MvcResult registrering = mockMvc.perform(post("/registrera").with(csrf())
                        .param("username", "nyss").param("password", "hemligt123")
                        .param("confirmPassword", "hemligt123"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) registrering.getRequest().getSession(false);

        mockMvc.perform(get("/").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
        verify(wineService, never()).search(any(), any());
    }
}
