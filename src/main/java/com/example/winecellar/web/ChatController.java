package com.example.winecellar.web;

import com.example.winecellar.application.ChatResult;
import com.example.winecellar.application.ChatService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.Conversation.ConversationId;
import com.example.winecellar.domain.User.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * AI-chatten om vinsamlingen (WINE-48) - se docs/adr/
 * 0021-wine-chat-conversational-llm-integration.md. Orkestreringen (gränser,
 * anrop mot assistenten) ligger i {@link ChatService} (ADR 0006-principen);
 * den här klassen tolkar bara requesten och avgör ägarskap/404, samma
 * uppdelning som {@link WineController}.
 */
@Controller
@RequestMapping("/chatt")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    public ChatController(ChatService chatService, UserRepository userRepository) {
        this.chatService = chatService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String list(Model model, Authentication authentication) {
        model.addAttribute("conversations", chatService.listConversations(owner(authentication)));
        return "chatt-lista";
    }

    /**
     * Det finns ingen tom konversation i den här appen (se ChatService) -
     * "starta ny" och "skicka första meddelandet" är samma handling.
     */
    @PostMapping
    public String start(
            @RequestParam String message, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (message == null || message.isBlank()) {
            return "redirect:/chatt";
        }
        ChatResult result = chatService.startConversation(owner(authentication), message.trim());
        return switch (result) {
            case ChatResult.Success success -> "redirect:/chatt/" + success.conversation().id().value();
            case ChatResult.LimitReached limitReached -> {
                redirectAttributes.addFlashAttribute("fel", limitReached.message());
                yield "redirect:/chatt";
            }
        };
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model, Authentication authentication) {
        Conversation conversation = findOwnedConversationOr404(id, authentication);
        model.addAttribute("conversation", conversation);
        model.addAttribute("messages", chatService.messages(conversation.id()));
        return "chatt";
    }

    @PostMapping("/{id}/meddelande")
    public String postMessage(
            @PathVariable Long id, @RequestParam String message,
            Authentication authentication, RedirectAttributes redirectAttributes) {
        Conversation conversation = findOwnedConversationOr404(id, authentication);
        if (message != null && !message.isBlank()) {
            ChatResult result = chatService.postMessage(conversation, message.trim());
            if (result instanceof ChatResult.LimitReached limitReached) {
                redirectAttributes.addFlashAttribute("fel", limitReached.message());
            }
        }
        return "redirect:/chatt/" + id;
    }

    @PostMapping("/{id}/radera")
    public String delete(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        chatService.deleteConversation(owner(authentication), new ConversationId(id));
        redirectAttributes.addFlashAttribute("feedback", "Konversationen borttagen");
        return "redirect:/chatt";
    }

    private Conversation findOwnedConversationOr404(Long id, Authentication authentication) {
        return chatService.findConversation(owner(authentication), new ConversationId(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private UserId owner(Authentication authentication) {
        return CurrentUser.owner(authentication, userRepository);
    }
}
