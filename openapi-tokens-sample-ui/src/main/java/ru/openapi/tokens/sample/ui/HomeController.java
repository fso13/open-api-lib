package ru.openapi.tokens.sample.ui;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sends the user to the page matching the granted role.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        final boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> UiModelAdvice.ROLE_ADMIN.equals(authority.getAuthority()));
        return admin ? "redirect:/admin/tokens" : "redirect:/ui/tokens";
    }
}
