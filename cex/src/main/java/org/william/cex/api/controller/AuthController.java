package org.william.cex.api.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.william.cex.api.dto.request.LoginRequest;
import org.william.cex.api.dto.request.RegisterUserRequest;
import org.william.cex.api.dto.response.AuthResponse;
import org.william.cex.domain.user.entity.User;
import org.william.cex.domain.user.service.UserService;
import org.william.cex.infrastructure.security.JwtTokenProvider;

@RestController
@RequestMapping("/v1/auth")
@Slf4j
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        try {
            User user = userService.registerUser(request.getEmail(), request.getPassword());
            String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().toString());

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(user.getRole().toString())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = userService.getUserByEmail(request.getEmail());
            // Note: In production, validate password here
            String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().toString());

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(user.getRole().toString())
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}

