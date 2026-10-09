package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationResult;
import com.example.winecellar.application.RegistrationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Registrering (WINE-11, se ADR 0013). Sedan WINE-59 (ADR 0026) är
 * användarnamnet en e-postadress och formuläret frågar BARA efter den; lösenordet väljs
 * först när länken i verifieringsmailet öppnas. Användaren loggas inte in automatiskt.
 */
@Controller
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/registrera")
    public String registrationPage() {
        return "registrera";
    }

    @PostMapping("/registrera")
    public String register(
            @RequestParam String username,
            Model model) {
        model.addAttribute("username", username);

        if (username == null || username.isBlank()) {
            model.addAttribute("error", "Fyll i din e-postadress.");
            return "registrera";
        }

        RegistrationResult result = registrationService.register(username);
        if (result instanceof RegistrationResult.InvalidEmail) {
            model.addAttribute("error", "Användarnamnet måste vara en e-postadress.");
            return "registrera";
        }
        if (result instanceof RegistrationResult.UsernameTaken) {
            model.addAttribute("error", "Användarnamnet är upptaget.");
            return "registrera";
        }
        // Post-Redirect-Get: informationen visas på inloggningssidan.
        return "redirect:/login?registered";
    }
}
