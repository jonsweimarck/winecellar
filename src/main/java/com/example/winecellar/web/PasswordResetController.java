package com.example.winecellar.web;

import com.example.winecellar.application.PasswordResetService;
import com.example.winecellar.application.TokenOutcome;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Glömt lösenord (WINE-59, se ADR 0026). Begäran ger ALLTID samma neutrala
 * svar. Länken i mailet öppnar ett formulär (GET förbrukar inget); det är
 * POST:en med nytt lösenord som förbrukar tokenet.
 */
@Controller
public class PasswordResetController {

    static final String NEUTRAL_MESSAGE = "Om adressen finns har ett mail skickats.";

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/glomt-losenord")
    public String requestPage() {
        return "glomt-losenord";
    }

    @PostMapping("/glomt-losenord")
    public String request(@RequestParam(required = false) String username, Model model) {
        passwordResetService.requestReset(username);
        model.addAttribute("info", NEUTRAL_MESSAGE);
        return "glomt-losenord";
    }

    @GetMapping("/aterstall-losenord")
    public String resetPage(@RequestParam(required = false) String token, Model model) {
        TokenOutcome outcome = passwordResetService.checkToken(token);
        if (outcome == TokenOutcome.SUCCESS) {
            model.addAttribute("token", token);
        } else {
            model.addAttribute("failed", true);
            model.addAttribute("error", message(outcome));
        }
        return "aterstall-losenord";
    }

    @PostMapping("/aterstall-losenord")
    public String reset(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String confirmPassword,
            Model model) {
        if (password == null || password.isBlank()) {
            return formWithError(model, token, "Fyll i ett nytt lösenord.");
        }
        if (!password.equals(confirmPassword)) {
            return formWithError(model, token, "Lösenorden matchar inte.");
        }
        TokenOutcome outcome = passwordResetService.resetPassword(token, password);
        if (outcome == TokenOutcome.SUCCESS) {
            return "redirect:/login?reset";
        }
        model.addAttribute("failed", true);
        model.addAttribute("error", message(outcome));
        return "aterstall-losenord";
    }

    private static String formWithError(Model model, String token, String error) {
        model.addAttribute("token", token);
        model.addAttribute("error", error);
        return "aterstall-losenord";
    }

    private static String message(TokenOutcome outcome) {
        return outcome == TokenOutcome.EXPIRED
                ? "Länken har gått ut. Begär en ny länk för att återställa lösenordet."
                : "Länken är ogiltig eller redan använd. Begär en ny länk för att återställa lösenordet.";
    }
}
