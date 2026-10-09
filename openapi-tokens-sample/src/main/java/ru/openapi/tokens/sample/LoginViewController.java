package ru.openapi.tokens.sample;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Custom form-login page (the POST is handled by Spring Security's {@code UsernamePasswordAuthenticationFilter}).
 */
@Controller
public class LoginViewController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
