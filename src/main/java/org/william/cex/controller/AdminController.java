package org.william.cex.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.william.cex.dto.request.AdminRegisterRequest;
import org.william.cex.dto.request.LoginRequest;
import org.william.cex.dto.response.AuthResponse;
import org.william.cex.service.AdminService;
import org.william.cex.service.FeeService;
import org.william.cex.entity.User;
import org.william.cex.infrastructure.security.AuthenticationUtils;
import org.william.cex.infrastructure.security.JwtTokenProvider;

@RestController
@RequestMapping("/v1/admin")
@Slf4j
public class AdminController {

    @Autowired
    private FeeService feeService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    @Autowired
    private AdminService adminService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerAdmin(@Valid @RequestBody AdminRegisterRequest request) {
        try {
            User admin = adminService.registerAdmin(request.getEmail(), request.getPassword(), request.getAdminKey());
            String token = jwtTokenProvider.generateToken(admin.getId(), admin.getEmail(), admin.getRole().toString());

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .userId(admin.getId())
                    .email(admin.getEmail())
                    .role(admin.getRole().toString())
                    .build();

            log.info("Admin registered successfully: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Admin registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error during admin registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginAdmin(@Valid @RequestBody LoginRequest request) {
        try {
            User admin = adminService.loginAdmin(request.getEmail(), request.getPassword());
            String token = jwtTokenProvider.generateToken(admin.getId(), admin.getEmail(), admin.getRole().toString());

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .userId(admin.getId())
                    .email(admin.getEmail())
                    .role(admin.getRole().toString())
                    .build();

            log.info("Admin login successful: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Admin login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}

