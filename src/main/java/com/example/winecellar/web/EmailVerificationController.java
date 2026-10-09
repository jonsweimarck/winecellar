package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.TokenOutcome;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Verifiering av e-postadress (WINE-59, se ADR 0026). Länken i mailet öppnar
 * en sida med en bekräfta-knapp; det är POST:en som förbrukar tokenet. Annars
 * hade en mailskanner/länkförhandsvisning som hämtar länken med GET förbrukat
 * den innan användaren hunnit klicka.
 */
@Controller
public class EmailVerificationController {

    private final RegistrationService registrationService;

    public EmailVerificationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/verifiera")
    public String confirmPage(@RequestParam(required = false) String token, Model model) {
        TokenOutcome outcome = registrationService.checkVerificationToken(token);
        if (outcome == TokenOutcome.SUCCESS) {
            model.addAttribute("token", token);
            return "verifiera";
        }
        model.addAttribute("failed", true);
        model.addAttribute("error", message(outcome));
        return "verifiera";
    }

    @PostMapping("/verifiera")
    public String verify(@RequestParam(required = false) String token, Model model) {
        TokenOutcome outcome = registrationService.verifyEmail(token);
        if (outcome == TokenOutcome.SUCCESS) {
            return "redirect:/login?verified";
        }
        model.addAttribute("failed", true);
        model.addAttribute("error", message(outcome));
        return "verifiera";
    }

    @GetMapping("/verifiera/ny")
    public String resendPage() {
        return "verifiera-ny";
    }

    @PostMapping("/verifiera/ny")
    public String resend(@RequestParam String username, Model model) {
        registrationService.resendVerification(username);
        // Samma svar oavsett om adressen finns, redan är verifierad eller begränsats.
        model.addAttribute("sent", true);
        return "verifiera-ny";
    }

    private static String message(TokenOutcome outcome) {
        return outcome == TokenOutcome.EXPIRED
                ? "Verifieringslänken har gått ut. Begär en ny länk."
                : "Verifieringslänken är ogiltig eller redan använd. Är kontot redan verifierat kan du logga in, "
                        + "annars kan du begära en ny länk.";
    }
}
