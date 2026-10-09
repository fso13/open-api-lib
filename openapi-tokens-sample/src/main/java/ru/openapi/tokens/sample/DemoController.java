package ru.openapi.tokens.sample;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('payments:read')")
    public Map<String, String> payments() {
        return Map.of("resource", "payments", "access", "granted");
    }
}
