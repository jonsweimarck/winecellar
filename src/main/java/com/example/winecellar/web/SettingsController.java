package com.example.winecellar.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Inställningssidan (WINE-37) - samlar det som tidigare låg som fristående
 * knappar direkt på vinlistan (import/export, se {@link ImportController}/
 * {@link ExportController}) och "Logga ut" (formuläret postar direkt mot
 * Spring Securitys `/logout`, hanteras inte här). Ingen egen modell/data -
 * sidan är bara länkar, se {@code installningar.html}.
 *
 * Mörkt/ljust läge finns med som en inaktiv platshållare i markupen
 * (Utseende-sektionen) - inte kopplad till något ännu.
 */
@Controller
public class SettingsController {

    @GetMapping("/installningar")
    public String installningar() {
        return "installningar";
    }
}
