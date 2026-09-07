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
 * Mörkt/ljust/auto-läge (Utseende-sektionen) är fullt fungerande, men
 * helt klientsidan - växlingen sköts av installningar.html:s eget
 * skript (sparar valet i localStorage) och läses av
 * fragments/tema.html på varje sidladdning. Ingen serverlogik här:
 * temat är en visningspreferens i webbläsaren, inte kontobunden data
 * (se ADR 0019). Verifieras av TemaIT, inte av något test mot den här
 * controllern.
 */
@Controller
public class SettingsController {

    @GetMapping("/installningar")
    public String installningar() {
        return "installningar";
    }
}
