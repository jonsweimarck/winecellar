package com.example.winecellar.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Bekräftelsesidor efter registrering, efter att lösenordet valts och efter ett återställt lösenord (WINE-59). Alla nås via
 * Post-Redirect-Get och bär ingen persondata i URL:en - texten är generell.
 */
@Controller
public class ConfirmationController {

    @GetMapping("/registrerad")
    public String registered() {
        return "registrerad";
    }

    @GetMapping("/losenord-andrat")
    public String passwordChanged() {
        return "losenord-andrat";
    }

    @GetMapping("/verifierad")
    public String verified() {
        return "verifierad";
    }
}
