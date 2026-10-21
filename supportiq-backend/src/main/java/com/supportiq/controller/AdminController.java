package com.supportiq.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /**
     * Admin health/ping endpoint used strictly to verify role-based access control.
     * Accessible only by users possessing the ADMIN role.
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
                "message", "Admin access confirmed. Pong!",
                "status", "UP",
                "role", "ADMIN"
        ));
    }
}
