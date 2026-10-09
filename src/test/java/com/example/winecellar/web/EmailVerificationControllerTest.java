package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.TokenOutcome;
import com.example.winecellar.application.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailVerificationController.class)
@Import(SecurityConfig.class)
class EmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void skaVisaBekräftaKnappUtanInloggningOchUtanAttFörbrukaTokenet() throws Exception {
        when(registrationService.checkVerificationToken("abc")).thenReturn(TokenOutcome.SUCCESS);

        mockMvc.perform(get("/verifiera").param("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"token\" value=\"abc\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        verify(registrationService, never()).verifyEmail("abc");
    }

    @Test
    void skaVisaFelOchLänkTillNyLänkVidUtgångenEllerOgiltigLänk() throws Exception {
        when(registrationService.checkVerificationToken("gammal")).thenReturn(TokenOutcome.EXPIRED);
        when(registrationService.checkVerificationToken("fel")).thenReturn(TokenOutcome.INVALID);

        mockMvc.perform(get("/verifiera").param("token", "gammal"))
                .andExpect(content().string(containsString("har gått ut")))
                .andExpect(content().string(containsString("/verifiera/ny")))
                .andExpect(content().string(not(containsString("name=\"token\""))));
        mockMvc.perform(get("/verifiera").param("token", "fel"))
                .andExpect(content().string(containsString("ogiltig eller redan använd")));
    }

    @Test
    void skaVerifieraViaPostOchOmdirigeraTillInloggningen() throws Exception {
        when(registrationService.verifyEmail("abc")).thenReturn(TokenOutcome.SUCCESS);

        mockMvc.perform(post("/verifiera").with(csrf()).param("token", "abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?verified"));
    }

    @Test
    void skaVisaFelVidPostMedOgiltigtToken() throws Exception {
        when(registrationService.verifyEmail("fel")).thenReturn(TokenOutcome.INVALID);

        mockMvc.perform(post("/verifiera").with(csrf()).param("token", "fel"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ogiltig eller redan använd")));
    }

    @Test
    void skaKrävaCsrfPåPost() throws Exception {
        mockMvc.perform(post("/verifiera").param("token", "abc")).andExpect(status().isForbidden());
        mockMvc.perform(post("/verifiera/ny").param("username", "a@b.se")).andExpect(status().isForbidden());
        verify(registrationService, never()).verifyEmail("abc");
    }

    @Test
    void skaGeSammaNeutraltSvarOavsettAdress() throws Exception {
        mockMvc.perform(get("/verifiera/ny")).andExpect(status().isOk());

        mockMvc.perform(post("/verifiera/ny").with(csrf()).param("username", "anna@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Om adressen finns")));
        mockMvc.perform(post("/verifiera/ny").with(csrf()).param("username", "okand@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Om adressen finns")));

        verify(registrationService).resendVerification("anna@example.com");
        verify(registrationService).resendVerification("okand@example.com");
    }
}
