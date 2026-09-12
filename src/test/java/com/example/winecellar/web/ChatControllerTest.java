package com.example.winecellar.web;

import com.example.winecellar.application.ChatResult;
import com.example.winecellar.application.ChatService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.ChatMessage.Role;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.Conversation.ConversationId;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * Testar bara webblagret (WINE-48) - orkestreringen (gränskontroller, anrop
 * mot assistenten) täcks av chatta-om-viner.feature mot applikationslagret,
 * se ADR 0006-principen. Verifierar faktiskt renderad HTML, inte bara att
 * anropet ger 200 (samma linje som övriga @WebMvcTest-klasser).
 */
@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest {

    private static final UserId ÄGARE = new UserId(1L);
    private static final ConversationId KONVERSATION_ID = new ConversationId(7L);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private UserRepository userRepository;

    private static Conversation konversation() {
        return new Conversation(KONVERSATION_ID, ÄGARE, "Vilket vin passar till rödkött?", Instant.now());
    }

    private void inloggadAnvändareFinns() {
        when(userRepository.findByUsername("testperson"))
                .thenReturn(Optional.of(new User(ÄGARE, "testperson", "hash", Instant.now(), 1)));
    }

    @Test
    void skaVisaKonversationslistan() throws Exception {
        inloggadAnvändareFinns();
        when(chatService.listConversations(ÄGARE)).thenReturn(List.of(konversation()));

        mockMvc.perform(get("/chatt").with(user("testperson")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vilket vin passar till rödkött?")))
                .andExpect(content().string(containsString("href=\"/chatt/7\"")));
    }

    @Test
    void skaVisaTomtLägeUtanKonversationer() throws Exception {
        inloggadAnvändareFinns();
        when(chatService.listConversations(ÄGARE)).thenReturn(List.of());

        mockMvc.perform(get("/chatt").with(user("testperson")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Inga konversationer än")));
    }

    @Test
    void skaNekaListanUtanInloggning() throws Exception {
        mockMvc.perform(get("/chatt"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void skaStartaNyKonversationOchOmdirigeraTillDen() throws Exception {
        inloggadAnvändareFinns();
        when(chatService.startConversation(eq(ÄGARE), eq("Vilket vin passar till fisk?")))
                .thenReturn(new ChatResult.Success(konversation(), assistantMessage("Prova en Chablis")));

        mockMvc.perform(post("/chatt").with(user("testperson")).with(csrf())
                        .param("message", "Vilket vin passar till fisk?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chatt/7"));
    }

    @Test
    void skaVisaFelOchStannaKvarOmKonversationsgränsenÄrNådd() throws Exception {
        inloggadAnvändareFinns();
        when(chatService.startConversation(eq(ÄGARE), any()))
                .thenReturn(new ChatResult.LimitReached("Du har redan för många konversationer"));

        mockMvc.perform(post("/chatt").with(user("testperson")).with(csrf())
                        .param("message", "En fråga till"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chatt"))
                .andExpect(flash().attribute("fel", "Du har redan för många konversationer"));
    }

    @Test
    void skaInteStartaKonversationMedTomtMeddelande() throws Exception {
        inloggadAnvändareFinns();

        mockMvc.perform(post("/chatt").with(user("testperson")).with(csrf())
                        .param("message", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chatt"));

        verify(chatService, never()).startConversation(any(), any());
    }

    @Test
    void skaVisaEnKonversationstråd() throws Exception {
        inloggadAnvändareFinns();
        when(chatService.findConversation(ÄGARE, KONVERSATION_ID)).thenReturn(Optional.of(konversation()));
        when(chatService.messages(KONVERSATION_ID)).thenReturn(List.of(
                new ChatMessage(null, KONVERSATION_ID, Role.USER, "Vilket vin passar till rödkött?", Instant.now()),
                new ChatMessage(null, KONVERSATION_ID, Role.ASSISTANT, "Barolo passar bra", Instant.now())));

        mockMvc.perform(get("/chatt/7").with(user("testperson")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vilket vin passar till rödkött?")))
                .andExpect(content().string(containsString("Barolo passar bra")));
    }

    @Test
    void skaGe404OmKonversationenInteFinnsEllerInteÄgs() throws Exception {
        inloggadAnvändareFinns();
        when(chatService.findConversation(ÄGARE, KONVERSATION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/chatt/7").with(user("testperson")))
                .andExpect(status().isNotFound());
    }

    @Test
    void skaPostaEttMeddelandeOchOmdirigeraTillbaka() throws Exception {
        inloggadAnvändareFinns();
        Conversation conversation = konversation();
        when(chatService.findConversation(ÄGARE, KONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(chatService.postMessage(eq(ÄGARE), eq(conversation), eq("Några fler förslag?")))
                .thenReturn(new ChatResult.Success(conversation, assistantMessage("Prova en Chianti")));

        mockMvc.perform(post("/chatt/7/meddelande").with(user("testperson")).with(csrf())
                        .param("message", "Några fler förslag?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chatt/7"));
    }

    @Test
    void skaVisaFelVidMeddelandegränsUtanAttNavigeraBort() throws Exception {
        inloggadAnvändareFinns();
        Conversation conversation = konversation();
        when(chatService.findConversation(ÄGARE, KONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(chatService.postMessage(eq(ÄGARE), eq(conversation), any()))
                .thenReturn(new ChatResult.LimitReached("Den här konversationen har för många meddelanden"));

        mockMvc.perform(post("/chatt/7/meddelande").with(user("testperson")).with(csrf())
                        .param("message", "Ännu en fråga"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chatt/7"))
                .andExpect(flash().attribute("fel", "Den här konversationen har för många meddelanden"));
    }

    @Test
    void skaGe404OmMeddelandePostasTillEnKonversationSomInteÄgs() throws Exception {
        inloggadAnvändareFinns();
        when(chatService.findConversation(ÄGARE, KONVERSATION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/chatt/7/meddelande").with(user("testperson")).with(csrf())
                        .param("message", "Ett meddelande"))
                .andExpect(status().isNotFound());

        verify(chatService, never()).postMessage(any(), any(), any());
    }

    @Test
    void skaRaderaKonversationenOchOmdirigeraTillListan() throws Exception {
        inloggadAnvändareFinns();

        mockMvc.perform(post("/chatt/7/radera").with(user("testperson")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chatt"))
                .andExpect(flash().attribute("feedback", "Konversationen borttagen"));

        verify(chatService).deleteConversation(ÄGARE, KONVERSATION_ID);
    }

    @Test
    void skaNekaSkapandeUtanInloggning() throws Exception {
        mockMvc.perform(post("/chatt").with(csrf()).param("message", "Fråga"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verify(chatService, never()).startConversation(any(), any());
    }

    private static ChatMessage assistantMessage(String content) {
        return new ChatMessage(null, KONVERSATION_ID, Role.ASSISTANT, content, Instant.now());
    }
}
