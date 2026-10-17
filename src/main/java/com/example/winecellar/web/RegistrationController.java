package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationResult;
import com.example.winecellar.application.RegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** Namnet på det dolda honeypot-fältet i registrera.html. */
    private static final Logger log = LoggerFactory.getLogger(RegistrationController.class);

    static final String HONEYPOT_FIELD = "kampanjref";

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
            @RequestParam(name = HONEYPOT_FIELD, required = false) String honeypot,
            Model model) {
        // Honeypot (WINE-65): ett dolt fält som en människa aldrig fyller i. Är det ifyllt är det en
        // bot - inget konto, inget mail, men exakt samma svar som en lyckad registrering.
        if (honeypot != null && !honeypot.isBlank()) {
            log.info("Registrering avvisad: honeypot-fältet var ifyllt (ingen adress loggas).");
            return "redirect:/registrerad";
        }
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
        // Post-Redirect-Get: en egen bekräftelsesida (ConfirmationController).
        return "redirect:/registrerad";
    }
}
