package com.example.winecellar.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.winecellar.application.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Bekräftelsesidorna är anonyma och visar tydliga besked med länk till inloggningen (WINE-59). */
@WebMvcTest(ConfirmationController.class)
@Import(SecurityConfig.class)
class ConfirmationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @Test
    void skaVisaBeskedOmAttMailetSkickatsUtanInloggning() throws Exception {
        mockMvc.perform(get("/registrerad"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kolla din e-post")))
                .andExpect(content().string(containsString("verifieringslänk")))
                .andExpect(content().string(containsString("href=\"/login\"")));
    }

    @Test
    void skaVisaBekräftelseMedLänkTillInloggningenEfterVerifiering() throws Exception {
        mockMvc.perform(get("/verifierad"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ditt konto är klart")))
                .andExpect(content().string(containsString("href=\"/login\"")));
    }
}
