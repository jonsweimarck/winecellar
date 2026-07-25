package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationResult;
import com.example.winecellar.application.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
     * Inga authorities behövs (WINE-15 tog bort hela rollbegreppet -
     * `SecurityConfig` kräver bara `authenticated()`, ingen route bryr
     * sig om roller längre).
     */
    private void loggaInAutomatiskt(String username, HttpServletRequest request, HttpServletResponse response) {
        var authentication = new UsernamePasswordAuthenticationToken(username, null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
