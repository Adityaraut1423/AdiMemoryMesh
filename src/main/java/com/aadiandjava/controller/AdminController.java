package com.aadiandjava.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aadiandjava.entity.Admin;
import com.aadiandjava.repository.AdminRepository;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminRepository adminRepository;

    public AdminController(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public record AdminLoginRequest(String username, String email, String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request) {
        String identifier = (request.username() != null && !request.username().isBlank())
                ? request.username().trim()
                : (request.email() != null ? request.email().trim() : "");
        String rawPassword = request.password() != null ? request.password() : "";

        if (identifier.isBlank() || rawPassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Username/Email and password are required."));
        }

        Optional<Admin> adminOpt = adminRepository.findByUsernameIgnoreCase(identifier);
        if (adminOpt.isEmpty()) {
            adminOpt = adminRepository.findByEmailIgnoreCase(identifier);
        }

        if (adminOpt.isEmpty() || !adminOpt.get().getPassword().equals(rawPassword)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid admin credentials."));
        }

        Admin admin = adminOpt.get();
        return ResponseEntity.ok(Map.of(
                "id", admin.getId(),
                "username", admin.getUsername(),
                "email", admin.getEmail(),
                "name", admin.getFullName(),
                "role", "ADMIN"
        ));
    }
}