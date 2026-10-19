package com.rehome.rehome.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TestController {

    @GetMapping("/api/public/hello")
    public Map<String, String> publicHello() {
        return Map.of("message", "Đây là endpoint public, không cần token");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/api/account/me")
    public Map<String, String> me(Authentication authentication) {
        return Map.of(
                "email", authentication.getName(),
                "authorities", authentication.getAuthorities().toString()
        );
    }

    @GetMapping("/api/admin/dashboard")
    public Map<String, String> adminDashboard() {
        return Map.of("message", "Chỉ ADMIN mới thấy được nội dung này");
    }
}
