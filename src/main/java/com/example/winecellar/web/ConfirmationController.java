package com.example.winecellar.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Bekräftelsesidor efter registrering och efter att lösenordet valts (WINE-59). Båda nås via
 * Post-Redirect-Get och bär ingen persondata i URL:en - texten är generell.
 */
@Controller
public class ConfirmationController {

    @GetMapping("/registrerad")
    public String registered() {
        return "registrerad";
    }

    @GetMapping("/verifierad")
    public String verified() {
        return "verifierad";
    }
}
