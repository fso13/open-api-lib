package ru.openapi.tokens.sample.ui;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the current user and navigation state to all UI templates.
 */
@ControllerAdvice(basePackages = "ru.openapi.tokens.sample.ui")
public class UiModelAdvice {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    @ModelAttribute
    public void populateCommonAttributes(Model model, Authentication authentication, HttpServletRequest request) {
        final boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        final boolean admin = authenticated && authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
        model.addAttribute("currentUser", authenticated ? authentication.getName() : null);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("currentPath", request.getRequestURI());
    }
}
