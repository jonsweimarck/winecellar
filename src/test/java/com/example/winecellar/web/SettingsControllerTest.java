package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testar bara webblagret (ingen egen affärslogik i SettingsController -
 * sidan är bara länkar). Verifierar att sidan faktiskt renderar länkarna
 * till import/export och logga-ut-formuläret (WINE-37), inte bara att
 * anropet ger 200.
 */
@WebMvcTest(SettingsController.class)
@Import(SecurityConfig.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

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
}
