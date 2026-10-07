package com.example.winecellar.web;

import com.example.winecellar.application.AdminService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Adminsidan (WINE-61, se ADR 0025). Åtkomst spärras server-side av
 * {@code SecurityConfig} (/admin/** kräver rollen ADMIN) OCH av
 * {@link AdminService}, som själv kontrollerar att den agerande är admin.
 * Knappar som döljs i mallen är bara bekvämlighet, inte skyddet.
 */
@Controller
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final SessionRegistry sessionRegistry;

    public AdminController(AdminService adminService, UserRepository userRepository,
                           SessionRegistry sessionRegistry) {
        this.adminService = adminService;
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/admin")
    public String admin(Model model, Authentication authentication) {
        UserId actor = CurrentUser.owner(authentication, userRepository);
        model.addAttribute("users", adminService.listUsers(actor));
        model.addAttribute("currentUserId", actor == null ? null : actor.value());
        return "admin";
    }

    @PostMapping("/admin/gor-till-admin")
    public String makeAdmin(@RequestParam long userId, Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        UserId actor = CurrentUser.owner(authentication, userRepository);
        String username = userRepository.findById(new UserId(userId)).map(User::username).orElse(null);
        if (adminService.makeAdmin(actor, new UserId(userId))) {
            redirectAttributes.addFlashAttribute("feedback",
                    username + " är nu admin (syns i menyn efter nästa inloggning)");
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/radera")
    public String delete(@RequestParam long userId, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        UserId actor = CurrentUser.owner(authentication, userRepository);
        String username = userRepository.findById(new UserId(userId)).map(User::username).orElse(null);
        if (adminService.deleteUser(actor, new UserId(userId))) {
            expireSessionsOf(username);
            redirectAttributes.addFlashAttribute("feedback", "Användaren " + username + " raderades");
        }
        return "redirect:/admin";
    }

    /**
     * En raderad användares pågående sessioner ska sluta fungera direkt -
     * annars skulle hens inloggade session leva kvar (en session läser inte
     * om UserDetailsService) tills den löper ut. Remember-me-cookien
     * behöver ingen åtgärd: den slås upp mot UserDetailsService, som inte
     * längre hittar användaren.
     */
    private void expireSessionsOf(String username) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String name = principal instanceof UserDetails details ? details.getUsername() : principal.toString();
            if (name.equals(username)) {
                sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        }
    }
}
