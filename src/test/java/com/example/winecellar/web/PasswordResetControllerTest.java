package com.example.winecellar.web;

import com.example.winecellar.application.PasswordResetService;
import com.example.winecellar.application.TokenOutcome;
import com.example.winecellar.application.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordResetController.class)
@Import(SecurityConfig.class)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void skaVisaFormuläretUtanInloggning() throws Exception {
        mockMvc.perform(get("/glomt-losenord"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void skaGeExaktSammaNeutraltSvarOavsettAdress() throws Exception {
        String known = mockMvc.perform(post("/glomt-losenord").with(csrf()).param("username", "anna@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Om adressen finns har ett mail skickats")))
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/glomt-losenord").with(csrf()).param("username", "okand@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(withoutCsrf(unknown)).isEqualTo(withoutCsrf(known));
        verify(passwordResetService).requestReset("anna@example.com");
        verify(passwordResetService).requestReset("okand@example.com");
    }

    @Test
    void skaKrävaCsrf() throws Exception {
        mockMvc.perform(post("/glomt-losenord").param("username", "anna@example.com"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/aterstall-losenord").param("token", "t").param("password", "x")
                .param("confirmPassword", "x")).andExpect(status().isForbidden());
        verify(passwordResetService, never()).requestReset(any());
        verify(passwordResetService, never()).resetPassword(any(), any());
    }

    @Test
    void skaVisaNyttLösenordsformulärVidGiltigLänkUtanAttFörbrukaDen() throws Exception {
        when(passwordResetService.checkToken("abc")).thenReturn(TokenOutcome.SUCCESS);

        mockMvc.perform(get("/aterstall-losenord").param("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"token\" value=\"abc\"")))
                .andExpect(content().string(containsString("name=\"confirmPassword\"")));

        verify(passwordResetService, never()).resetPassword(any(), any());
    }

    @Test
    void skaVisaMeddelandeOmUtgångenOchOgiltigLänk() throws Exception {
        when(passwordResetService.checkToken("gammal")).thenReturn(TokenOutcome.EXPIRED);
        when(passwordResetService.checkToken("fel")).thenReturn(TokenOutcome.INVALID);

        mockMvc.perform(get("/aterstall-losenord").param("token", "gammal"))
                .andExpect(content().string(containsString("Länken har gått ut")))
                .andExpect(content().string(not(containsString("name=\"password\""))));
        mockMvc.perform(get("/aterstall-losenord").param("token", "fel"))
                .andExpect(content().string(containsString("ogiltig eller redan använd")));
    }

    @Test
    void skaBytaLösenordOchOmdirigeraTillInloggningen() throws Exception {
        when(passwordResetService.resetPassword("abc", "nyttLösen456")).thenReturn(TokenOutcome.SUCCESS);

        mockMvc.perform(post("/aterstall-losenord").with(csrf()).param("token", "abc")
                        .param("password", "nyttLösen456").param("confirmPassword", "nyttLösen456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset"));
    }

    @Test
    void skaVisaUtgångenLänkVidPost() throws Exception {
        when(passwordResetService.resetPassword("gammal", "nyttLösen456")).thenReturn(TokenOutcome.EXPIRED);

        mockMvc.perform(post("/aterstall-losenord").with(csrf()).param("token", "gammal")
                        .param("password", "nyttLösen456").param("confirmPassword", "nyttLösen456"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Länken har gått ut")));
    }

    @Test
    void skaNekaOmLösenordenInteMatcharEllerSaknasUtanAttFörbrukaLänken() throws Exception {
        mockMvc.perform(post("/aterstall-losenord").with(csrf()).param("token", "abc")
                        .param("password", "a").param("confirmPassword", "b"))
                .andExpect(content().string(containsString("matchar inte")))
                .andExpect(content().string(containsString("name=\"token\" value=\"abc\"")));
        mockMvc.perform(post("/aterstall-losenord").with(csrf()).param("token", "abc")
                        .param("password", "").param("confirmPassword", ""))
                .andExpect(content().string(containsString("Fyll i")));

        verify(passwordResetService, never()).resetPassword(any(), any());
    }

    private static String withoutCsrf(String html) {
        return html.replaceAll("name=\"_csrf\"\\s+value=\"[^\"]*\"", "name=\"_csrf\" value=\"X\"");
    }
}
