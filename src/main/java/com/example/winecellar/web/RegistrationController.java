package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationResult;
import com.example.winecellar.application.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class RegistrationController {

    private final RegistrationService registrationService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

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
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model, HttpServletRequest request, HttpServletResponse response) {
        model.addAttribute("username", username);

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            model.addAttribute("error", "Fyll i användarnamn och lösenord.");
            return "registrera";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Lösenorden matchar inte.");
            return "registrera";
        }

        RegistrationResult result = registrationService.register(username, password);
        if (result instanceof RegistrationResult.UsernameTaken) {
            model.addAttribute("error", "Användarnamnet är upptaget.");
            return "registrera";
        }

        loggaInAutomatiskt(username, request, response);
        return "redirect:/";
    }

    /**
     * Loggar in den nyregistrerade användaren direkt, utan ett separat
     * inloggningssteg - bygger en redan-autentiserad Authentication
     * (3-argumentskonstruktorn) och sparar den i sessionen via samma
     * mekanism SecurityContextHolderFilter/SecurityContextRepository
     * annars sköter automatiskt vid en vanlig formLogin-rundtur.
     *
     * ROLE_ADMIN är medvetet hårdkodat, inte hämtat från någonstans -
     * WINE-15 tar bort rollbegreppet helt när ADMIN/READONLY-kontona
     * försvinner. Fram tills dess behöver en nyregistrerad användare
     * ADMIN för att alls kunna använda appen (SecurityConfigs
     * route-regler kräver fortfarande den rollen) - även om scopingen
     * till den egna listan inte är på plats förrän WINE-13, så alla
     * användare delar i praktiken samma vinlista under tiden.
     */
    private void loggaInAutomatiskt(String username, HttpServletRequest request, HttpServletResponse response) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
