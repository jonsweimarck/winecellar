package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Inställningssidan (WINE-37) - samlar det som tidigare låg som fristående
 * knappar direkt på vinlistan (import/export, se {@link ImportController}/
 * {@link ExportController}) och "Logga ut" (formuläret postar direkt mot
 * Spring Securitys `/logout`, hanteras inte här).
 *
 * Mörkt/ljust/auto-läge (Utseende-sektionen) är fullt fungerande, men
 * helt klientsidan - växlingen sköts av installningar.html:s eget
 * skript (sparar valet i localStorage) och läses av
 * fragments/tema.html på varje sidladdning. Ingen serverlogik här:
 * temat är en visningspreferens i webbläsaren, inte kontobunden data
 * (se ADR 0019). Verifieras av TemaIT, inte av något test mot den här
 * controllern.
 *
 * Vinlistans "Antal flaskor minst"-standardval (WINE-41, semantiken
 * ändrad från "fler än" till "minst" i WINE-42) är däremot kontobunden
 * data - lagras på {@code User.defaultMinQuantityFilter}, inte i
 * localStorage, eftersom det ska gälla den inloggade användaren oavsett
 * vilken enhet hen loggar in från nästa gång (till skillnad från temat,
 * som medvetet är per webbläsare).
 */
@Controller
public class SettingsController {

    private final UserRepository userRepository;

    public SettingsController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/installningar")
    public String installningar(Model model, Authentication authentication) {
        model.addAttribute("minQuantityFilterDefault",
                CurrentUser.defaultMinQuantityFilter(authentication, userRepository));
        return "installningar";
    }

    /**
     * Sparar vinlistans "Antal flaskor minst"-standardval - läses av
     * `WineController.wineCellar(...)` som fallback när `GET /` saknar en
     * explicit `minQuantity`-queryparameter. Blankt/oparsbart fält faller
     * tillbaka till 1, samma default som ett helt nytt konto får (se
     * RegistrationService) - ingen anledning att kräva ett ifyllt fält
     * här när "1" redan är den rimliga standarden.
     */
    @PostMapping("/installningar/antal-flaskor-filter")
    public String saveDefaultMinQuantityFilter(
            @RequestParam(required = false) String minQuantity,
            Authentication authentication, RedirectAttributes redirectAttributes) {
        int value = parseMinQuantity(minQuantity);
        userRepository.findByUsername(authentication.getName()).ifPresent(user ->
                userRepository.save(new User(user.id(), user.username(), user.hashedPassword(), user.createdAt(), value)));
        redirectAttributes.addFlashAttribute("feedback", "Standardfilter sparat");
        return "redirect:/installningar";
    }

    private static int parseMinQuantity(String value) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
