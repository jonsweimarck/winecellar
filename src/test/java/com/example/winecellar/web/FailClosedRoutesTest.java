package com.example.winecellar.web;

import com.example.winecellar.support.TestUsers;
import com.example.winecellar.application.ChatService;
import com.example.winecellar.application.ImportPreviewService;
import com.example.winecellar.application.LabelInterpretationService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine.WineId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WINE-63: fail-closed (WINE-61) gäller alla skrivande/läsande vägar, inte bara
 * vinlistan och exporten. En autentiserad principal vars användare saknas i
 * databasen (raderad medan sessionen lever) skickas till /login och ingenting
 * läses, sparas eller anropas externt. Motkontroller visar att samma anrop
 * lyckas när användaren finns, så att testerna inte är gröna av fel orsak.
 * Urvalet av WineController-vägar är representativt, inte uttömmande - de delar
 * samma CurrentUser-anrop.
 */
@WebMvcTest({SettingsController.class, ChatController.class, ImportController.class, WineController.class})
@Import({SecurityConfig.class, ImportPreviewService.class})
class FailClosedRoutesTest {

    private static final String LOGIN = "http://localhost/login";
    private static final User FINNS = TestUsers.verifiedUser(new UserId(1L), "finns", "hash", Instant.now(), 1, false, false, Instant.now());

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private WineService wineService;

    @MockBean
    private LabelInterpretationService labelInterpretationService;

    @MockBean
    private ChatService chatService;

    @BeforeEach
    void stubUsers() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername("finns")).thenReturn(Optional.of(FINNS));
    }

    @Test
    void skaNekaBeggaSettingsPostVägarnaNärAnvändarenSaknas() throws Exception {
        mockMvc.perform(post("/installningar/antal-flaskor-filter").param("minQuantity", "3")
                        .with(user("spöke")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));
        mockMvc.perform(post("/installningar/eget-betyg-skala").param("ownRatingFromScale", "on")
                        .with(user("spöke")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));

        verify(userRepository, never()).save(any());
    }

    @Test
    void skaSparaSettingsNärAnvändarenFinns() throws Exception {
        mockMvc.perform(post("/installningar/antal-flaskor-filter").param("minQuantity", "3")
                        .with(user("finns")).with(csrf()))
                .andExpect(redirectedUrl("/installningar"));
        mockMvc.perform(post("/installningar/eget-betyg-skala").param("ownRatingFromScale", "on")
                        .with(user("finns")).with(csrf()))
                .andExpect(redirectedUrl("/installningar"));

        verify(userRepository, times(2)).save(any());
    }

    @Test
    void skaNekaChattenNärAnvändarenSaknas() throws Exception {
        mockMvc.perform(get("/chatt").with(user("spöke")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));
        mockMvc.perform(post("/chatt").param("message", "Hej").with(user("spöke")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));

        // Ingen konversation läses/sparas och assistenten (bakom ChatService) anropas aldrig
        verifyNoInteractions(chatService);
    }

    @Test
    void skaVisaChattlistanNärAnvändarenFinns() throws Exception {
        when(chatService.listConversations(FINNS.id())).thenReturn(List.of());

        mockMvc.perform(get("/chatt").with(user("finns")))
                .andExpect(status().isOk());

        verify(chatService).listConversations(FINNS.id());
    }

    @Test
    void skaNekaImportPostVägarnaNärAnvändarenSaknasUtanAttSpara() throws Exception {
        MockMultipartFile fil = new MockMultipartFile("fil", "vin.xlsx",
                "application/octet-stream", new byte[] {1, 2, 3});

        // GET /import är medvetet INTE fail-closed: det är ett statiskt formulär utan
        // användardata (ingen CurrentUser-uppslagning), så det testas inte här.
        mockMvc.perform(multipart("/import").file(fil).with(user("spöke")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));
        mockMvc.perform(post("/import/commit").with(user("spöke")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));

        verify(wineService, never()).save(any());
        verify(wineService, never()).increaseQuantityBy(any(), any(), anyInt());
    }

    @Test
    void skaVisaImportformuläretNärAnvändarenFinns() throws Exception {
        mockMvc.perform(get("/import").with(user("finns")))
                .andExpect(status().isOk());
    }

    @Test
    void skaNekaVinPostVägarnaNärAnvändarenSaknas() throws Exception {
        mockMvc.perform(multipart("/wines").param("name", "Barolo").param("quantity", "1")
                        .with(user("spöke")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));
        mockMvc.perform(post("/wines/5/radera").with(user("spöke")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));

        verify(wineService, never()).save(any());
        verify(wineService, never()).removeWine(any(), any());
    }

    @Test
    void skaRaderaVinNärAnvändarenFinns() throws Exception {
        mockMvc.perform(post("/wines/5/radera").with(user("finns")).with(csrf()))
                .andExpect(redirectedUrl("/"));

        verify(wineService).removeWine(new WineId(5L), FINNS.id());
    }
    @Test
    void skaVisaImportformuläretÄvenNärAnvändarenSaknas() throws Exception {
        // MEDVETET undantag: GET /import är ett statiskt formulär utan användardata.
        // Skyddet ligger i att POST /import och /import/commit är fail-closed via
        // CurrentUser.owner.
        mockMvc.perform(get("/import").with(user("spöke")))
                .andExpect(status().isOk());
    }

    @Test
    void skaNekaRedigeraOchEtikettskanningNärAnvändarenSaknas() throws Exception {
        mockMvc.perform(get("/wines/5/redigera").with(user("spöke")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));
        mockMvc.perform(multipart("/wines/5/redigera").param("name", "Barolo").param("quantity", "1")
                        .with(user("spöke")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));
        // /wines/tolka-etikett slår upp användaren FÖRE det betalda LLM-anropet.
        mockMvc.perform(multipart("/wines/tolka-etikett")
                        .file(new MockMultipartFile("bild", "e.png", "image/png", new byte[] {1}))
                        .with(user("spöke")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN));

        verify(wineService, never()).save(any());
        verifyNoInteractions(labelInterpretationService);
    }
}
