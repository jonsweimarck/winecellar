package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
 * Testar bara webblagret (ingen egen affärslogik utöver vinlistans
 * "Antal flaskor fler än"-standardval, WINE-41). Verifierar att sidan
 * faktiskt renderar länkarna till import/export och logga-ut-formuläret
 * (WINE-37), inte bara att anropet ger 200.
 */
@WebMvcTest(SettingsController.class)
@Import(SecurityConfig.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    private static final UserId MIN_ANVÄNDARE_ID = new UserId(1L);

    @Test
    void skaVisaLänkarTillImportOchExportSamtLoggaUt() throws Exception {
        mockMvc.perform(get("/installningar").with(user("testperson")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/import\"")))
                .andExpect(content().string(containsString("href=\"/export\"")))
                .andExpect(content().string(containsString("/logout")))
                .andExpect(content().string(containsString("Logga ut")));
    }

    @Test
    void skaNekaUtanInloggning() throws Exception {
        mockMvc.perform(get("/installningar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void skaVisaSparatStandardvärdeFörAntalFlaskorFilter() throws Exception {
        when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                new User(MIN_ANVÄNDARE_ID, "testperson", "hash", Instant.now(), 2)));

        mockMvc.perform(get("/installningar").with(user("testperson")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Antal flaskor fler än")))
                .andExpect(content().string(containsString("name=\"minQuantity\"")))
                .andExpect(content().string(containsString("value=\"2\"")));
    }

    @Test
    void skaSparaNyttStandardvärdeOchOmdirigeraTillbaka() throws Exception {
        when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                new User(MIN_ANVÄNDARE_ID, "testperson", "hash", Instant.now(), 0)));

        mockMvc.perform(post("/installningar/antal-flaskor-filter")
                        .with(user("testperson")).with(csrf())
                        .param("minQuantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/installningar"))
                .andExpect(flash().attribute("feedback", "Standardfilter sparat"));

        verify(userRepository).save(argThat(saved ->
                saved.id().equals(MIN_ANVÄNDARE_ID) && saved.defaultMinQuantityFilter() == 2));
    }

    @Test
    void skaSparaNollOmFältetLämnasTomt() throws Exception {
        when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                new User(MIN_ANVÄNDARE_ID, "testperson", "hash", Instant.now(), 3)));

        mockMvc.perform(post("/installningar/antal-flaskor-filter")
                        .with(user("testperson")).with(csrf())
                        .param("minQuantity", ""))
                .andExpect(status().is3xxRedirection());

        verify(userRepository).save(argThat(saved -> saved.defaultMinQuantityFilter() == 0));
    }

    @Test
    void skaNekaSparningUtanInloggning() throws Exception {
        mockMvc.perform(post("/installningar/antal-flaskor-filter")
                        .with(csrf())
                        .param("minQuantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verify(userRepository, never()).save(any());
    }
}
